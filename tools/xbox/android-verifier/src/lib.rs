//! Android stand-in for `rustls-platform-verifier` (Bannerlator, for the Android build of the Xbox backend).
//!
//! Same entry point reqwest 0.13 uses (`Verifier::new(provider)`), but certificate checks go to rustls'
//! own WebPKI verifier with Android's system trust store: the PEM files in `SSL_CERT_DIR` if set, else the
//! updatable Conscrypt store (`/apex/com.android.conscrypt/cacerts`, Android 14+), else
//! `/system/etc/security/cacerts`. Nothing is relaxed: hostname, chain and signature checks are WebPKI's.

use std::path::PathBuf;
use std::sync::Arc;

use rustls::client::danger::{HandshakeSignatureValid, ServerCertVerified, ServerCertVerifier};
use rustls::client::WebPkiServerVerifier;
use rustls::crypto::CryptoProvider;
use rustls::pki_types::pem::PemObject;
use rustls::pki_types::{CertificateDer, ServerName, UnixTime};
use rustls::{DigitallySignedStruct, Error, RootCertStore, SignatureScheme};

#[derive(Debug)]
pub struct Verifier {
    inner: Arc<WebPkiServerVerifier>,
}

fn system_roots() -> RootCertStore {
    let mut dirs: Vec<PathBuf> = Vec::new();
    if let Some(d) = std::env::var_os("SSL_CERT_DIR") {
        dirs.extend(std::env::split_paths(&d));
    }
    dirs.push("/apex/com.android.conscrypt/cacerts".into());
    dirs.push("/system/etc/security/cacerts".into());
    let mut roots = RootCertStore::empty();
    for dir in dirs {
        if let Ok(entries) = std::fs::read_dir(&dir) {
            for entry in entries.flatten() {
                if let Ok(bytes) = std::fs::read(entry.path()) {
                    for cert in CertificateDer::pem_slice_iter(&bytes).flatten() {
                        let _ = roots.add(cert);
                    }
                }
            }
        }
        if !roots.is_empty() {
            break;
        }
    }
    roots
}

impl Verifier {
    pub fn new(crypto_provider: Arc<CryptoProvider>) -> Result<Self, Error> {
        Self::with_roots(system_roots(), crypto_provider)
    }

    pub fn new_with_extra_roots(
        extra_roots: impl IntoIterator<Item = CertificateDer<'static>>,
        crypto_provider: Arc<CryptoProvider>,
    ) -> Result<Self, Error> {
        let mut roots = system_roots();
        for cert in extra_roots {
            let _ = roots.add(cert);
        }
        Self::with_roots(roots, crypto_provider)
    }

    fn with_roots(roots: RootCertStore, crypto_provider: Arc<CryptoProvider>) -> Result<Self, Error> {
        if roots.is_empty() {
            return Err(Error::General("no system CA certificates found".into()));
        }
        let inner = WebPkiServerVerifier::builder_with_provider(Arc::new(roots), crypto_provider)
            .build()
            .map_err(|e| Error::General(e.to_string()))?;
        Ok(Self { inner })
    }
}

impl ServerCertVerifier for Verifier {
    fn verify_server_cert(
        &self,
        end_entity: &CertificateDer<'_>,
        intermediates: &[CertificateDer<'_>],
        server_name: &ServerName<'_>,
        ocsp_response: &[u8],
        now: UnixTime,
    ) -> Result<ServerCertVerified, Error> {
        self.inner.verify_server_cert(end_entity, intermediates, server_name, ocsp_response, now)
    }

    fn verify_tls12_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, Error> {
        self.inner.verify_tls12_signature(message, cert, dss)
    }

    fn verify_tls13_signature(
        &self,
        message: &[u8],
        cert: &CertificateDer<'_>,
        dss: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, Error> {
        self.inner.verify_tls13_signature(message, cert, dss)
    }

    fn supported_verify_schemes(&self) -> Vec<SignatureScheme> {
        self.inner.supported_verify_schemes()
    }
}
