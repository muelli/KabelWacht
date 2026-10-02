#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Tobias Mueller and KabelWacht contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Derive a complete Android signing identity from a single seed string.

One high-entropy seed string deterministically yields an EC P-256 private
key and a keystore passphrase; combined with the PUBLIC certificate that is
committed to git (signing/<role>-cert.pem), that is everything Android
signing needs. CI therefore requires exactly ONE secret per signing identity
instead of four (keystore blob, store password, key alias, key password).

Normal use (certificate already committed):

    SEED='...' scripts/derive-signing-key.py <role> <out.p12>

First-time bootstrap (generates the certificate; commit the .pem afterwards):

    SEED='...' scripts/derive-signing-key.py --bootstrap <role> <out.p12>

<role> is "app", "fdroid" or "play"; it namespaces the derivation so the
identities can never collide even if (mis)configured with the same seed.
The derived keystore passphrase is printed on stdout (nothing else is).

"play" is RSA rather than EC because Google Play accepts nothing else ("Must
be an RSA key of 2048 bits or more"), and it is only an *upload* key: Play
re-signs the APKs it delivers with a key it holds, so this key authenticates
uploads and nothing more. Play can re-register a replacement if it is ever
lost, which is why it is reasonable to derive it from the same seed as the
irreplaceable one.

Why the certificate lives in git: Android identifies a signer by the exact
certificate bytes, and ECDSA self-signatures are randomized — regenerating
the certificate each run would look like a different signer. The certificate
is public material (it is embedded in every APK), so pinning it in the
repository is both safe and transparent. The script refuses to build a
keystore whose derived key does not match the pinned certificate.

DERIVATION IS A CONTRACT (v1) — the seed *is* the private key, and this
algorithm is the map between them. It must never change for existing seeds;
any future change must introduce a new version label and keep v1 intact:

  PRK        = HMAC-SHA256(key="kabelwacht-signing-v1", msg=seed)
  scalar     = (int(HMAC-SHA512(PRK, role || ":ec-scalar")) mod (n-1)) + 1
               where n is the SECP256R1 group order; key = scalar * G
  passphrase = base64url(HMAC-SHA512(PRK, role || ":passphrase")[:24])
  alias      = the role string itself

  RSA roles instead take each prime from its own deterministic stream,
  block_i = HMAC-SHA512(PRK, role || ":" || label || ":" || i), concatenated
  to the prime size; the top two bits and the low bit are set, and the
  candidate is incremented by 2 until it is prime and coprime with e=65537.
  p is the larger of the two primes. Nothing here consumes randomness, so
  the same seed always yields the same key.
"""
import base64
import datetime
import hashlib
import hmac
import math
import os
import sys

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, rsa
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

SALT = b"kabelwacht-signing-v1"
COMMON_NAME = {
    "app": "KabelWacht",
    "fdroid": "KabelWacht F-Droid Repo",
    "play": "KabelWacht Play Upload",
}
# Roles whose key is RSA (Google Play refuses EC); everything else is EC P-256.
RSA_ROLES = {"play"}
RSA_BITS = 2048
E = 65537
# SECP256R1 group order.
N = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551

# Trial division before the expensive test discards most candidates cheaply.
_SMALL_PRIMES = [
    p for p in range(3, 2000)
    if all(p % q for q in range(3, int(p ** 0.5) + 1, 2)) and p % 2
]


def _probable_prime(n: int) -> bool:
    """Miller-Rabin over fixed bases. Fixed, not random, keeps it deterministic."""
    for p in _SMALL_PRIMES:
        if n % p == 0:
            return n == p
    d, r = n - 1, 0
    while d % 2 == 0:
        d //= 2
        r += 1
    # Base 2 first: it rejects nearly every composite that survived the sieve.
    for a in [2] + _SMALL_PRIMES[:31]:
        x = pow(a, d, n)
        if x in (1, n - 1):
            continue
        for _ in range(r - 1):
            x = x * x % n
            if x == n - 1:
                break
        else:
            return False
    return True


def _derive_prime(prk: bytes, role: str, label: str, bits: int) -> int:
    """First prime at or above a seed-derived candidate of exactly `bits` bits."""
    buf = b""
    counter = 0
    while len(buf) * 8 < bits:
        buf += hmac.new(prk, f"{role}:{label}:{counter}".encode(), hashlib.sha512).digest()
        counter += 1
    n = int.from_bytes(buf[: bits // 8], "big")
    n |= (1 << (bits - 1)) | (1 << (bits - 2))  # full size, and p*q keeps it
    n |= 1                                       # odd
    while not (_probable_prime(n) and math.gcd(E, n - 1) == 1):
        n += 2
    return n


def _derive_rsa(prk: bytes, role: str) -> rsa.RSAPrivateKey:
    p = _derive_prime(prk, role, "rsa-p", RSA_BITS // 2)
    q = _derive_prime(prk, role, "rsa-q", RSA_BITS // 2)
    if p == q:
        raise SystemExit("derived primes collided — this should be impossible")
    if q > p:
        p, q = q, p  # cryptography expects iqmp = q^-1 mod p
    d = pow(E, -1, (p - 1) * (q - 1) // math.gcd(p - 1, q - 1))
    return rsa.RSAPrivateNumbers(
        p=p,
        q=q,
        d=d,
        dmp1=d % (p - 1),
        dmq1=d % (q - 1),
        iqmp=pow(q, -1, p),
        public_numbers=rsa.RSAPublicNumbers(E, p * q),
    ).private_key()


def cert_path(role: str) -> str:
    return os.path.join(os.path.dirname(__file__), "..", "signing", f"{role}-cert.pem")


def main() -> int:
    args = sys.argv[1:]
    bootstrap = "--bootstrap" in args
    args = [a for a in args if a != "--bootstrap"]
    if len(args) != 2 or args[0] not in COMMON_NAME:
        sys.exit(f"usage: SEED='...' {sys.argv[0]} [--bootstrap] {{app|fdroid|play}} <out.p12>")
    role, out_path = args

    seed = os.environ.get("SEED", "")
    if len(seed.strip()) < 32:
        sys.exit("SEED must be set and at least 32 characters of high-entropy data")

    prk = hmac.new(SALT, seed.encode(), hashlib.sha256).digest()

    def expand(label: str) -> bytes:
        return hmac.new(prk, f"{role}:{label}".encode(), hashlib.sha512).digest()

    if role in RSA_ROLES:
        key = _derive_rsa(prk, role)
    else:
        scalar = int.from_bytes(expand("ec-scalar"), "big") % (N - 1) + 1
        key = ec.derive_private_key(scalar, ec.SECP256R1())
    passphrase = base64.urlsafe_b64encode(expand("passphrase")[:24]).decode()

    pem = cert_path(role)
    if bootstrap:
        if os.path.exists(pem):
            sys.exit(f"{pem} already exists — refusing to overwrite an established identity")
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, COMMON_NAME[role])])
        cert = (
            x509.CertificateBuilder()
            .subject_name(name)
            .issuer_name(name)
            .public_key(key.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(datetime.datetime(2026, 1, 1, tzinfo=datetime.timezone.utc))
            .not_valid_after(datetime.datetime(2126, 1, 1, tzinfo=datetime.timezone.utc))
            .sign(key, hashes.SHA256())
        )
        os.makedirs(os.path.dirname(pem), exist_ok=True)
        with open(pem, "wb") as f:
            f.write(cert.public_bytes(serialization.Encoding.PEM))
    else:
        try:
            with open(pem, "rb") as f:
                cert = x509.load_pem_x509_certificate(f.read())
        except FileNotFoundError:
            sys.exit(f"{pem} not found — run once with --bootstrap and commit the .pem")

    if cert.public_key().public_numbers() != key.public_key().public_numbers():
        sys.exit(
            f"SEED does not match the committed certificate {pem} — wrong seed, "
            "or the certificate belongs to a different identity"
        )

    p12 = pkcs12.serialize_key_and_certificates(
        role.encode(),
        key,
        cert,
        None,
        serialization.BestAvailableEncryption(passphrase.encode()),
    )
    with open(out_path, "wb") as f:
        f.write(p12)
    print(passphrase)
    return 0


if __name__ == "__main__":
    sys.exit(main())
