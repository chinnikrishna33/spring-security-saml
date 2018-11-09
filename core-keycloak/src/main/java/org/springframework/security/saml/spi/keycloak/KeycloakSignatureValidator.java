/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.springframework.security.saml.spi.keycloak;

import java.security.Key;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureException;

import org.springframework.security.saml.SamlException;
import org.springframework.security.saml.saml2.SignableSaml2Object;
import org.springframework.security.saml.saml2.key.KeyData;
import org.springframework.security.saml.saml2.signature.AlgorithmMethod;
import org.springframework.security.saml.saml2.signature.CanonicalizationMethod;
import org.springframework.security.saml.saml2.signature.DigestMethod;
import org.springframework.security.saml.saml2.signature.Signature;
import org.springframework.security.saml.saml2.signature.SignatureException;
import org.springframework.security.saml.util.X509Utilities;

import org.keycloak.rotation.HardcodedKeyLocator;
import org.keycloak.rotation.KeyLocator;
import org.keycloak.saml.processing.core.util.XMLSignatureUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Optional.ofNullable;
import static org.springframework.security.saml.saml2.Namespace.NS_SIGNATURE;

class KeycloakSignatureValidator {
	static Map<String, Signature> validateSignature(SamlObjectHolder parsed, List<KeyData> keys) {
		if (keys == null || keys.isEmpty()) {
			return emptyMap();
		}
		try {
			NodeList nl = parsed.getDocument().getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
			if (nl == null || nl.getLength() == 0) {
				return emptyMap();
			}
			Map<String, Signature> valid = new LinkedHashMap<>();
			for (int i = 0; i < nl.getLength(); i++) {
				Node signatureNode = nl.item(i);
				Signature validSignature = validateSignature(signatureNode, keys);
				valid.put(getSignatureHashKey(validSignature), validSignature);
			}
			return valid;
		} catch (SignatureException e) {
			throw new SignatureException("Unable to validate signature for object:" + parsed.getSamlObject(), e);
		} catch (Exception e) {
			throw new SamlException("Unable to get signature for class:" + parsed.getSamlObject().getClass(), e);
		}
	}

	static Signature validateSignature(Node signatureNode, List<KeyData> keys) {
		final List<Key> publicKeys = getPublicKeys(keys);
		final TrackingIterator<Key> trackingIterator = new TrackingIterator<>(publicKeys);
		KeyLocator keyLocator = new HardcodedKeyLocator(publicKeys) {
			@Override
			public Key getKey(String kid) {
				if (publicKeys.size() == 1) {
					return iterator().next();
				}
				else {
					return null;
				}
			}

			@Override
			public Iterator<Key> iterator() {
				trackingIterator.reset();
				return trackingIterator;
			}
		};
		try {
			boolean ok = XMLSignatureUtil.validateSingleNode(signatureNode, keyLocator);
			if (ok) {
				KeyData keyData = keys.get(trackingIterator.getCurrentIndex());
				Signature sig = getSignature((Element) signatureNode)
					.setValidated(true)
					.setValidatingKey(keyData);
				return sig;
			}
			else {
				throw new SignatureException("Unable to validate signature using " + keys.size() + " keys.");
			}
		} catch (MarshalException | XMLSignatureException e) {
			throw new SignatureException(e);
		}
	}

	static String getSignatureHashKey(Signature signature) {
		return getSignatureHashKey(signature.getSignatureValue(), signature.getDigestValue());
	}

	static List<Key> getPublicKeys(List<KeyData> keys) {
		return Collections.unmodifiableList(
			ofNullable(keys).orElse(emptyList())
				.stream()
				.map(k -> getPublicKey(k.getCertificate()))
				.collect(Collectors.toList())
		);
	}

	static Signature getSignature(Element n) {
		Signature result = new Signature()
			.setCanonicalizationAlgorithm(
				CanonicalizationMethod.fromUrn(
					getAttributeFromChildNode(n, NS_SIGNATURE, "CanonicalizationMethod", "Algorithm")
				)
			)
			.setDigestValue(
				getTextFromChildNode(n, NS_SIGNATURE, "DigestValue")
			)
			.setDigestAlgorithm(
				DigestMethod.fromUrn(
					getAttributeFromChildNode(n, NS_SIGNATURE, "DigestMethod", "Algorithm")
				)
			)
			.setSignatureValue(
				getTextFromChildNode(n, NS_SIGNATURE, "SignatureValue")
			)
			.setSignatureAlgorithm(
				AlgorithmMethod.fromUrn(
					getAttributeFromChildNode(n, NS_SIGNATURE, "SignatureMethod", "Algorithm")
				)
			);


		return result;
	}

	static String getSignatureHashKey(String signatureValue, String digestValue) {
		return new StringBuffer("Signature Hash Key[Sig=")
			.append(signatureValue)
			.append("; Digest=")
			.append(digestValue)
			.append("]")
			.toString();
	}

	static PublicKey getPublicKey(String certPem) {
		if (certPem == null) {
			throw new SamlException("Public certificate is missing.");
		}

		try {
			byte[] certbytes = X509Utilities.getDER(certPem);
			Certificate cert = X509Utilities.getCertificate(certbytes);
			//TODO - should be based off of config
			//((X509Certificate) cert).checkValidity();
			return cert.getPublicKey();
		} catch (CertificateException ex) {
			throw new SamlException("Certificate is not valid.", ex);
		} catch (Exception e) {
			throw new SamlException("Could not decode cert", e);
		}
	}

	static String getAttributeFromChildNode(Element n,
											String namespace,
											String elementName,
											String attributeName) {
		NodeList list = n.getElementsByTagNameNS(namespace, elementName);
		if (list == null || list.getLength() == 0) {
			return null;
		}
		Node item = list.item(0).getAttributes().getNamedItem(attributeName);
		if (item == null) {
			return null;
		}
		return item.getTextContent();
	}

	static String getTextFromChildNode(Element n,
									   String namespace,
									   String elementName) {
		NodeList list = n.getElementsByTagNameNS(namespace, elementName);
		if (list == null || list.getLength() == 0) {
			return null;
		}
		return list.item(0).getTextContent();
	}

	static void assignSignatureToObject(Map<String, Signature> signatureMap,
										SignableSaml2Object desc,
										Element descriptorSignature) {
		if (descriptorSignature != null) {
			Signature signature = KeycloakSignatureValidator.getSignature(descriptorSignature);
			String hashKey = KeycloakSignatureValidator.getSignatureHashKey(signature);
			desc.setSignature(signatureMap.get(hashKey));
		}
	}
}
