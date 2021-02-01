# new example

This folder contains a sample server and a sample client to test some settings. Currently the ocsp feature / status request extension is not working as intended (even when a certificate is revoked the connection is established).

The setup is the following (for test purposes only!):
- a root ca signs certificates directly
- next to the server.jar a keystore.jks (password: changeme) with the server certificate, the server private key and the ca certificate must exist
- next to the client.jar a keystore.jks (password: changeme) with the client certificate and the client private key  must exist

## example steps to setup a mini-pki with ocsp (for test purposes only!)

sources:

- https://bhashineen.medium.com/create-your-own-ocsp-server-ffb212df8e63
- https://coderwall.com/p/3t4xka/import-private-key-and-certificate-into-java-keystore
- https://support.code42.com/Administrator/6/Configuring/Install_a_CA-signed_SSL_certificate_for_HTTPS_console_access

### basic setup

create some required files and folders

```bash=
mkdir -p demoCA/newcerts
touch demoCA/index.txt
echo "01" > demoCA/serial
```

### edit configuration

get the default configuration

```bash=
wget https://raw.githubusercontent.com/openssl/openssl/master/apps/openssl.cnf
mv openssl.cnf validation.cnf
```

add the following to (below) the [usr_cert] section in validation.cnf

```plaintext=
authorityInfoAccess = OCSP;URI:http://127.0.0.1:8080

[ v3_OCSP ]
basicConstraints = CA:FALSE
keyUsage = nonRepudiation, digitalSignature, keyEncipherment
extendedKeyUsage = OCSPSigning
```

### generate ca

```bash=
openssl genrsa -out rootCA.key 1024
openssl req -new -x509 -days 3650 -key rootCA.key -out rootCA.crt -config validation.cnf
```

### generate certificate & signing request

```bash=
openssl genrsa -out tlsserver.key 1024
openssl req -new -x509 -days 3650 -key tlsserver.key -out tlsserver.crt -config validation.cnf
openssl x509 -x509toreq -in tlsserver.crt -out tlsserver.csr -signkey tlsserver.key
```

### sign and do things

```bash=
openssl ca -batch -startdate 150813080000Z -enddate 250813090000Z -keyfile rootCA.key -cert rootCA.crt -policy policy_anything -config validation.cnf -notext -out tlsserver.crt -infiles tlsserver.csr
```

### create oscp required things

set "common name" field to 127.0.0.1 (or the value you've defined in the validation.cnf)

```bash=
openssl req -new -nodes -out ocspSigning.csr -keyout ocspSigning.key
openssl ca -keyfile rootCA.key -cert rootCA.crt -in ocspSigning.csr -out ocspSigning.crt -config validation.cnf
```

### start ocsp responder

```bash=
openssl ocsp -index demoCA/index.txt -port 8080 -rsigner ocspSigning.crt -rkey ocspSigning.key -CA rootCA.crt -text -out log.txt
```

### verify certificate manually

```bash=
openssl ocsp -CAfile rootCA.crt -issuer rootCA.crt -cert tlsserver.crt -url http://127.0.0.1:8080 -resp_text -noverify
```

### revoke certificate

```bash=
openssl ca -keyfile rootCA.key -cert rootCA.crt -revoke tlsserver.crt -config validation.cnf
```

### add generated certificate and private key to a keystore

first: convert to .p12 file

(set an export-password, otherwise the next step (import) will fail) 

```bash=
openssl pkcs12 -export -in tlsserver.crt -inkey tlsserver.key -chain -CAfile rootCA.crt -name tlsserver -out tlsserver.p12
```

second: import .p12

```bash=
keytool -importkeystore -srckeystore /path/to/tlsserver.p12 -destkeystore keystore.jks -srcstoretype pkcs12
```

third (optional): import root ca certificate

```bash=
keytool -importcert -file /path/to/rootCA.crt -keystore keystore.jks -trustcacerts
```