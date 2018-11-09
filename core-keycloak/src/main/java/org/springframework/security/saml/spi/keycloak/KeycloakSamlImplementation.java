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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import javax.xml.crypto.dsig.XMLObject;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.springframework.security.saml.SamlException;
import org.springframework.security.saml.SamlKeyException;
import org.springframework.security.saml.saml2.ImplementationHolder;
import org.springframework.security.saml.saml2.Saml2Object;
import org.springframework.security.saml.saml2.SignableSaml2Object;
import org.springframework.security.saml.saml2.attribute.Attribute;
import org.springframework.security.saml.saml2.attribute.AttributeNameFormat;
import org.springframework.security.saml.saml2.authentication.Assertion;
import org.springframework.security.saml.saml2.authentication.AssertionCondition;
import org.springframework.security.saml.saml2.authentication.AudienceRestriction;
import org.springframework.security.saml.saml2.authentication.AuthenticationContext;
import org.springframework.security.saml.saml2.authentication.AuthenticationContextClassReference;
import org.springframework.security.saml.saml2.authentication.AuthenticationRequest;
import org.springframework.security.saml.saml2.authentication.AuthenticationStatement;
import org.springframework.security.saml.saml2.authentication.Conditions;
import org.springframework.security.saml.saml2.authentication.Issuer;
import org.springframework.security.saml.saml2.authentication.LogoutReason;
import org.springframework.security.saml.saml2.authentication.LogoutRequest;
import org.springframework.security.saml.saml2.authentication.LogoutResponse;
import org.springframework.security.saml.saml2.authentication.NameIdPolicy;
import org.springframework.security.saml.saml2.authentication.NameIdPrincipal;
import org.springframework.security.saml.saml2.authentication.OneTimeUse;
import org.springframework.security.saml.saml2.authentication.RequestedAuthenticationContext;
import org.springframework.security.saml.saml2.authentication.Response;
import org.springframework.security.saml.saml2.authentication.Status;
import org.springframework.security.saml.saml2.authentication.StatusCode;
import org.springframework.security.saml.saml2.authentication.Subject;
import org.springframework.security.saml.saml2.authentication.SubjectConfirmation;
import org.springframework.security.saml.saml2.authentication.SubjectConfirmationData;
import org.springframework.security.saml.saml2.authentication.SubjectConfirmationMethod;
import org.springframework.security.saml.saml2.encrypt.DataEncryptionMethod;
import org.springframework.security.saml.saml2.encrypt.KeyEncryptionMethod;
import org.springframework.security.saml.saml2.key.KeyData;
import org.springframework.security.saml.saml2.key.KeyType;
import org.springframework.security.saml.saml2.metadata.Binding;
import org.springframework.security.saml.saml2.metadata.Endpoint;
import org.springframework.security.saml.saml2.metadata.IdentityProvider;
import org.springframework.security.saml.saml2.metadata.IdentityProviderMetadata;
import org.springframework.security.saml.saml2.metadata.Metadata;
import org.springframework.security.saml.saml2.metadata.NameId;
import org.springframework.security.saml.saml2.metadata.Provider;
import org.springframework.security.saml.saml2.metadata.ServiceProvider;
import org.springframework.security.saml.saml2.metadata.ServiceProviderMetadata;
import org.springframework.security.saml.saml2.metadata.SsoProvider;
import org.springframework.security.saml.saml2.signature.Signature;
import org.springframework.security.saml.spi.SamlKeyStoreProvider;
import org.springframework.security.saml.spi.SpringSecuritySaml;
import org.springframework.security.saml.util.X509Utilities;
import org.springframework.util.CollectionUtils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.keycloak.dom.saml.v2.assertion.AssertionType;
import org.keycloak.dom.saml.v2.assertion.AttributeStatementType;
import org.keycloak.dom.saml.v2.assertion.AttributeType;
import org.keycloak.dom.saml.v2.assertion.AudienceRestrictionType;
import org.keycloak.dom.saml.v2.assertion.AuthnContextClassRefType;
import org.keycloak.dom.saml.v2.assertion.AuthnContextType;
import org.keycloak.dom.saml.v2.assertion.AuthnStatementType;
import org.keycloak.dom.saml.v2.assertion.ConditionAbstractType;
import org.keycloak.dom.saml.v2.assertion.ConditionsType;
import org.keycloak.dom.saml.v2.assertion.EncryptedAssertionType;
import org.keycloak.dom.saml.v2.assertion.EncryptedElementType;
import org.keycloak.dom.saml.v2.assertion.NameIDType;
import org.keycloak.dom.saml.v2.assertion.OneTimeUseType;
import org.keycloak.dom.saml.v2.assertion.StatementAbstractType;
import org.keycloak.dom.saml.v2.assertion.SubjectConfirmationDataType;
import org.keycloak.dom.saml.v2.assertion.SubjectConfirmationType;
import org.keycloak.dom.saml.v2.assertion.SubjectType;
import org.keycloak.dom.saml.v2.metadata.AttributeConsumingServiceType;
import org.keycloak.dom.saml.v2.metadata.EndpointType;
import org.keycloak.dom.saml.v2.metadata.EntitiesDescriptorType;
import org.keycloak.dom.saml.v2.metadata.EntityDescriptorType;
import org.keycloak.dom.saml.v2.metadata.ExtensionsType;
import org.keycloak.dom.saml.v2.metadata.IDPSSODescriptorType;
import org.keycloak.dom.saml.v2.metadata.IndexedEndpointType;
import org.keycloak.dom.saml.v2.metadata.KeyDescriptorType;
import org.keycloak.dom.saml.v2.metadata.KeyTypes;
import org.keycloak.dom.saml.v2.metadata.RequestedAttributeType;
import org.keycloak.dom.saml.v2.metadata.RoleDescriptorType;
import org.keycloak.dom.saml.v2.metadata.SPSSODescriptorType;
import org.keycloak.dom.saml.v2.metadata.SSODescriptorType;
import org.keycloak.dom.saml.v2.protocol.AuthnContextComparisonType;
import org.keycloak.dom.saml.v2.protocol.AuthnRequestType;
import org.keycloak.dom.saml.v2.protocol.LogoutRequestType;
import org.keycloak.dom.saml.v2.protocol.NameIDPolicyType;
import org.keycloak.dom.saml.v2.protocol.RequestedAuthnContextType;
import org.keycloak.dom.saml.v2.protocol.ResponseType;
import org.keycloak.dom.saml.v2.protocol.StatusResponseType;
import org.keycloak.dom.saml.v2.protocol.StatusType;
import org.keycloak.saml.common.exceptions.ProcessingException;
import org.keycloak.saml.common.util.DocumentUtil;
import org.keycloak.saml.common.util.StaxUtil;
import org.keycloak.saml.processing.core.parsers.saml.SAMLParser;
import org.keycloak.saml.processing.core.saml.v2.writers.SAMLAssertionWriter;
import org.keycloak.saml.processing.core.saml.v2.writers.SAMLMetadataWriter;
import org.keycloak.saml.processing.core.saml.v2.writers.SAMLRequestWriter;
import org.keycloak.saml.processing.core.util.JAXPValidationUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static org.keycloak.saml.processing.api.saml.v2.sig.SAML2Signature.configureIdAttribute;
import static org.springframework.security.saml.saml2.Namespace.NS_SIGNATURE;
import static org.springframework.security.saml.util.StringUtils.getHostFromUrl;
import static org.springframework.security.saml.util.StringUtils.isUrl;
import static org.springframework.util.StringUtils.hasText;

public class KeycloakSamlImplementation extends SpringSecuritySaml<KeycloakSamlImplementation> {

	private static final Log logger = LogFactory.getLog(KeycloakSamlImplementation.class);
	private SamlKeyStoreProvider samlKeyStoreProvider = new SamlKeyStoreProvider() {
	};

	public KeycloakSamlImplementation(Clock time) {
		super(time);
	}

	private Metadata determineMetadataType(List<? extends Provider> ssoProviders) {
		Metadata result = new Metadata();
		long sps = ssoProviders.stream().filter(p -> p instanceof ServiceProvider).count();
		long idps = ssoProviders.stream().filter(p -> p instanceof IdentityProvider).count();

		if (ssoProviders.size() == sps) {
			result = new ServiceProviderMetadata();
		}
		else if (ssoProviders.size() == idps) {
			result = new IdentityProviderMetadata();
		}
		result.setProviders(ssoProviders);
		return result;
	}

	public static SecretKey generateKeyFromURI(DataEncryptionMethod algoURI) {
		throw new UnsupportedOperationException();
//		try {
//			String jceAlgorithmName = JCEMapper.getJCEKeyAlgorithmFromURI(algoURI.toString());
//			int keyLength = JCEMapper.getKeyLengthFromURI(algoURI.toString());
//			return generateKey(jceAlgorithmName, keyLength, null);
//		} catch (NoSuchAlgorithmException | NoSuchProviderException e) {
//			throw new SamlException(e);
//		}
	}

	public SamlKeyStoreProvider getSamlKeyStoreProvider() {
		return samlKeyStoreProvider;
	}

	public KeycloakSamlImplementation setSamlKeyStoreProvider(SamlKeyStoreProvider samlKeyStoreProvider) {
		this.samlKeyStoreProvider = samlKeyStoreProvider;
		return this;
	}

	protected void bootstrap() {
		org.apache.xml.security.Init.init();
	}

	@Override
	public long toMillis(Duration duration) {
		long now = System.currentTimeMillis();
		Date d = new Date(now);
		long millis = duration.getTimeInMillis(d);
		return Math.abs(millis - now);
	}

	@Override
	public Duration toDuration(long millis) {
		try {
			return DatatypeFactory.newInstance().newDuration(millis);
		} catch (DatatypeConfigurationException e) {
			throw new SamlException(e);
		}
	}

	@Override
	public String toXml(Saml2Object saml2Object) {
		Object result = null;
		if (saml2Object instanceof Metadata) {
			result = internalToXml((Metadata) saml2Object);
		}
		else if (saml2Object instanceof AuthenticationRequest) {
			result = internalToXml((AuthenticationRequest) saml2Object);
		}
		else if (saml2Object instanceof Assertion) {
			try {
				result = internalToXml((Assertion) saml2Object);
			} catch (URISyntaxException e) {
				throw new SamlException(e);
			}
		}
//		else if (saml2Object instanceof Response) {
//			result = internalToXml((Response) saml2Object);
//		}
//		else if (saml2Object instanceof LogoutRequest) {
//			result = internalToXml((LogoutRequest) saml2Object);
//		}
//		else if (saml2Object instanceof LogoutResponse) {
//			result = internalToXml((LogoutResponse) saml2Object);
//		}
		if (result == null) {
			throw new SamlException("To xml transformation not supported for: " +
				saml2Object != null ?
				saml2Object.getClass().getName() :
				"null"
			);
		}
		String xml = marshallToXml(result);
		if (saml2Object instanceof SignableSaml2Object) {
			SignableSaml2Object signable = (SignableSaml2Object) saml2Object;
			xml = signObject(xml, signable);
		}
		return xml;
	}

	@Override
	public Saml2Object resolve(String xml, List<KeyData> verificationKeys, List<KeyData> localKeys) {
		return resolve(xml.getBytes(UTF_8), verificationKeys, localKeys);
	}

	public Saml2Object resolve(byte[] xml, List<KeyData> verificationKeys, List<KeyData> localKeys) {
		SamlObjectHolder parsed = parse(xml);
		Map<String, Signature> signatureMap = KeycloakSignatureValidator.validateSignature(parsed, verificationKeys);
		Saml2Object result = null;
		if (parsed.getSamlObject() instanceof EntityDescriptorType) {
			result = resolveMetadata(
				(EntityDescriptorType) parsed.getSamlObject(),
				signatureMap
			);
		}
		else if (parsed.getSamlObject() instanceof EntitiesDescriptorType) {
			result =
				resolveMetadata(
					(EntitiesDescriptorType) parsed.getSamlObject(),
					signatureMap
				);
			;
		}
		else if (parsed.getSamlObject() instanceof AuthnRequestType) {
			result = resolveAuthenticationRequest(
				(AuthnRequestType) parsed.getSamlObject(),
				signatureMap
			);
		}
//		else if (parsed instanceof org.opensaml.saml.saml2.core.Assertion) {
//			result = resolveAssertion(
//				(org.opensaml.saml.saml2.core.Assertion) parsed,
//				verificationKeys,
//				localKeys,
//				false
//			);
//		}
//		else if (parsed instanceof org.opensaml.saml.saml2.core.Response) {
//			result = resolveResponse((org.opensaml.saml.saml2.core.Response) parsed, verificationKeys, localKeys)
//				.setSignature(signature);
//		}
//		else if (parsed instanceof org.opensaml.saml.saml2.core.LogoutRequest) {
//			result = resolveLogoutRequest(
//				(org.opensaml.saml.saml2.core.LogoutRequest) parsed,
//				verificationKeys,
//				localKeys
//			)
//				.setSignature(signature);
//		}
//		else if (parsed instanceof org.opensaml.saml.saml2.core.LogoutResponse) {
//			result = resolveLogoutResponse(
//				(org.opensaml.saml.saml2.core.LogoutResponse) parsed,
//				verificationKeys,
//				localKeys
//			)
//				.setSignature(signature);
//		}
		if (result != null) {
			if (result instanceof ImplementationHolder) {
				((ImplementationHolder) result).setImplementation(parsed);
				((ImplementationHolder) result).setOriginalXML(new String(xml, StandardCharsets.UTF_8));
			}
			return result;
		}
		throw new SamlException("Deserialization not yet supported for class: " + parsed.getClass());
	}

	@Override
	public Signature validateSignature(Saml2Object saml2Object, List<KeyData> trustedKeys) {
		if (saml2Object == null || saml2Object.getImplementation() == null) {
			throw new SamlException("No object to validate signature against.");
		}

		if (saml2Object instanceof Assertion && ((Assertion) saml2Object).isEncrypted()) {
			//we don't need to validate the signature
			//of an assertion that was successfully decrypted
			return null;
		}

		if (trustedKeys == null || trustedKeys.isEmpty()) {
			throw new SamlKeyException("At least one verification key has to be provided");
		}

		if (saml2Object.getImplementation() instanceof SamlObjectHolder) {
			Map<String, Signature> signatureMap =
				KeycloakSignatureValidator.validateSignature(
					(SamlObjectHolder) saml2Object.getImplementation(),
					trustedKeys
				);
			if (!signatureMap.isEmpty()) {
				return signatureMap.entrySet().iterator().next().getValue();
			}
			else {
				return null;
			}
		}
		else {
			throw new SamlException(
				"Unrecognized object type:" + saml2Object.getImplementation().getClass().getName()
			);
		}
	}

//	protected Encrypter getEncrypter(KeyData key,
//									 KeyEncryptionMethod keyAlgorithm,
//									 DataEncryptionMethod dataAlgorithm) {
//		Credential credential = getCredential(key, getCredentialsResolver(key));
//
//		SecretKey secretKey = generateKeyFromURI(dataAlgorithm);
//		BasicCredential dataCredential = new BasicCredential(secretKey);
//		DataEncryptionParameters dataEncryptionParameters = new DataEncryptionParameters();
//		dataEncryptionParameters.setEncryptionCredential(dataCredential);
//		dataEncryptionParameters.setAlgorithm(dataAlgorithm.toString());
//
//		KeyEncryptionParameters keyEncryptionParameters = new KeyEncryptionParameters();
//		keyEncryptionParameters.setEncryptionCredential(credential);
//		keyEncryptionParameters.setAlgorithm(keyAlgorithm.toString());
//
//		Encrypter encrypter = new Encrypter(dataEncryptionParameters, asList(keyEncryptionParameters));
//
//		return encrypter;
//	}

	protected EntityDescriptorType internalToXml(Metadata<? extends Metadata> metadata) {
		EntityDescriptorType desc = new EntityDescriptorType(metadata.getEntityId());
		if (hasText(metadata.getId())) {
			desc.setID(metadata.getId());
		}
		else {
			desc.setID("M" + UUID.randomUUID().toString());
		}
		List<RoleDescriptorType> descriptors = getRoleDescriptors(metadata);
		descriptors.stream().forEach(
			d -> {
				if (d instanceof SSODescriptorType) {
					desc.addChoiceType(
						EntityDescriptorType.EDTChoiceType.oneValue(
							new EntityDescriptorType.EDTDescriptorChoiceType((SSODescriptorType) d)
						)
					);
				}
			}
		);
//		if (metadata.getSigningKey() != null) {
//			signObject(desc, metadata.getSigningKey(), metadata.getAlgorithm(), metadata.getDigest());
//		}
		return desc;
	}

//	protected Decrypter getDecrypter(KeyData key) {
//		Credential credential = getCredential(key, getCredentialsResolver(key));
//		KeyInfoCredentialResolver resolver = new StaticKeyInfoCredentialResolver(credential);
//		Decrypter decrypter = new Decrypter(null, resolver, encryptedKeyResolver);
//		decrypter.setRootInNewDocument(true);
//		return decrypter;
//	}

	protected AuthnRequestType internalToXml(AuthenticationRequest request) {
		XMLGregorianCalendar instant =
			getXmlGregorianCalendar(ofNullable(request.getIssueInstant()).orElse(DateTime.now()));
		String id = ofNullable(request.getId()).orElse("an" + UUID.randomUUID().toString());
		AuthnRequestType auth = new AuthnRequestType(id, instant);
		auth.setForceAuthn(request.isForceAuth());
		auth.setIsPassive(request.isPassive());
		try {
			auth.setProtocolBinding(new URI(request.getBinding().toString()));
			auth.setAssertionConsumerServiceURL(new URI(request.getAssertionConsumerService().getLocation()));
			auth.setDestination(new URI(request.getDestination().getLocation()));
		} catch (URISyntaxException e) {
			throw new SamlException(e);
		}
		// Azure AD as IdP will not accept index if protocol binding or AssertationCustomerServiceURL is set.
		//auth.setAssertionConsumerServiceIndex(request.getAssertionConsumerService().getIndex());
		auth.setNameIDPolicy(getNameIDPolicy(request.getNameIdPolicy()));
		auth.setRequestedAuthnContext(getRequestedAuthenticationContext(request));
		auth.setIssuer(toIssuer(request.getIssuer()));
		return auth;
	}

	protected String marshallToXml(Object object) {
		StringWriter writer = new StringWriter();
		if (object instanceof EntityDescriptorType) {
			try {
				XMLStreamWriter streamWriter = StaxUtil.getXMLStreamWriter(writer);
				SAMLMetadataWriter metadataWriter = new KeycloakSamlMetadataWriter(streamWriter);
				metadataWriter.writeEntityDescriptor((EntityDescriptorType) object);
				streamWriter.flush();
				return writer.getBuffer().toString();
			} catch (ProcessingException | XMLStreamException e) {
				throw new SamlException(e);
			}
		}
		else if (object instanceof AuthnRequestType) {
			try {
				XMLStreamWriter streamWriter = StaxUtil.getXMLStreamWriter(writer);
				SAMLRequestWriter requestWriter = new SAMLRequestWriter(streamWriter);
				requestWriter.write((AuthnRequestType) object);
				streamWriter.flush();
				return writer.getBuffer().toString();
			} catch (ProcessingException | XMLStreamException e) {
				throw new SamlException(e);
			}
		}
		else if (object instanceof AssertionType) {
			try {
				XMLStreamWriter streamWriter = StaxUtil.getXMLStreamWriter(writer);
				SAMLAssertionWriter assertionWriter = new SAMLAssertionWriter(streamWriter);
				assertionWriter.write((AssertionType) object);
				streamWriter.flush();
				return writer.getBuffer().toString();
			} catch (ProcessingException | XMLStreamException e) {
				throw new SamlException(e);
			}
		}
		else {
			throw new UnsupportedOperationException();
		}
	}

	public String signObject(String xml,
							 SignableSaml2Object signable) {
		if (signable.getSigningKey() == null) {
			return xml;
		}

		SamlKeyStoreProvider keystoreProvider = new SamlKeyStoreProvider() {};
		KeycloakStaxSigner signer = new KeycloakStaxSigner(keystoreProvider);
		return signer.sign(signable, xml);
	}

	protected List<RoleDescriptorType> getRoleDescriptors(Metadata<? extends Metadata> metadata) {
		List<RoleDescriptorType> result = new LinkedList<>();
		for (SsoProvider<? extends SsoProvider> p : metadata.getSsoProviders()) {
			RoleDescriptorType roleDescriptor = null;
			if (p instanceof ServiceProvider) {
				ServiceProvider sp = (ServiceProvider) p;
				SPSSODescriptorType descriptor = new SPSSODescriptorType(sp.getProtocolSupportEnumeration());
				roleDescriptor = descriptor;
				descriptor.setAuthnRequestsSigned(sp.isAuthnRequestsSigned());
				descriptor.setWantAssertionsSigned(sp.isWantAssertionsSigned());
				for (NameId id : p.getNameIds()) {
					descriptor.addNameIDFormat(id.toString());
				}
				for (int i = 0; i < sp.getAssertionConsumerService().size(); i++) {
					Endpoint ep = sp.getAssertionConsumerService().get(i);
					descriptor.addAssertionConsumerService(getIndexedEndpointType(ep, i));
				}
				for (int i = 0; i < sp.getArtifactResolutionService().size(); i++) {
					Endpoint ep = sp.getArtifactResolutionService().get(i);
					descriptor.addArtifactResolutionService(getArtifactResolutionService(ep, i));
				}
				for (int i = 0; i < sp.getSingleLogoutService().size(); i++) {
					Endpoint ep = sp.getSingleLogoutService().get(i);
					descriptor.addSingleLogoutService(getSingleLogoutService(ep));
				}
				if (sp.getRequestedAttributes() != null && !sp.getRequestedAttributes().isEmpty()) {
					descriptor.addAttributeConsumerService(getAttributeConsumingService(sp.getRequestedAttributes()));
				}

			}
			else if (p instanceof IdentityProvider) {
				IdentityProvider idp = (IdentityProvider) p;
				IDPSSODescriptorType descriptor = new IDPSSODescriptorType(
					ofNullable(idp.getProtocolSupportEnumeration()).orElse(emptyList())
				);
				roleDescriptor = descriptor;
				descriptor.setWantAuthnRequestsSigned(idp.getWantAuthnRequestsSigned());
				for (NameId id : p.getNameIds()) {
					descriptor.addNameIDFormat(id.toString());
				}
				for (int i = 0; i < idp.getSingleSignOnService().size(); i++) {
					Endpoint ep = idp.getSingleSignOnService().get(i);
					descriptor.addSingleSignOnService(getSingleSignOnService(ep, i));
				}
				for (int i = 0; i < p.getSingleLogoutService().size(); i++) {
					Endpoint ep = p.getSingleLogoutService().get(i);
					descriptor.addSingleLogoutService(getSingleLogoutService(ep));
				}
				for (int i = 0; i < p.getArtifactResolutionService().size(); i++) {
					Endpoint ep = p.getArtifactResolutionService().get(i);
					descriptor.addArtifactResolutionService(getArtifactResolutionService(ep, i));
				}
			}
			long now = getTime().millis();
			if (p.getCacheDuration() != null) {
				roleDescriptor.setCacheDuration(p.getCacheDuration());
			}
			roleDescriptor.setValidUntil(getXmlGregorianCalendar(p.getValidUntil()));
			//roleDescriptor.addSupportedProtocol(NS_PROTOCOL);
			roleDescriptor.setID(ofNullable(p.getId()).orElse(UUID.randomUUID().toString()));

			for (KeyData key : p.getKeys()) {
				roleDescriptor.addKeyDescriptor(getKeyDescriptor(key));
			}

			//md:extensions
			Endpoint requestInitiation = p.getRequestInitiation();
			Endpoint discovery = p.getDiscovery();
			if (requestInitiation != null || discovery != null) {
				ExtensionsType extensionsType = new ExtensionsType();
				if (requestInitiation != null) {
					try {
						EndpointType ri = new EndpointType(
							new URI(requestInitiation.getBinding().toString()),
							new URI(requestInitiation.getLocation())
						);
						if (hasText(requestInitiation.getResponseLocation())) {
							ri.setResponseLocation(new URI(requestInitiation.getResponseLocation()));
						}
						extensionsType.addExtension(ri);
					} catch (URISyntaxException e) {
						throw new SamlException(e);
					}
				}
				if (discovery != null) {
					try {
						IndexedEndpointType d = new IndexedEndpointType(
							new URI(discovery.getBinding().toString()),
							new URI(discovery.getLocation())
						);
						if (hasText(discovery.getResponseLocation())) {
							d.setResponseLocation(new URI(requestInitiation.getResponseLocation()));
						}
						if (discovery.getIndex() >= 0) {
							d.setIndex(discovery.getIndex());
						}
						d.setIsDefault(discovery.isDefault() ? true : null);
						extensionsType.addExtension(d);
					} catch (URISyntaxException e) {
						throw new SamlException(e);
					}
				}
				roleDescriptor.setExtensions(extensionsType);
			}
			roleDescriptor.setID(p.getId());
			result.add(roleDescriptor);
		}
		return result;
	}

	private XMLGregorianCalendar getXmlGregorianCalendar(DateTime date) {
		if (date == null) {
			return null;
		}
		try {
			DatatypeFactory df = DatatypeFactory.newInstance();
			return df.newXMLGregorianCalendar(date.toGregorianCalendar());
		} catch (DatatypeConfigurationException e) {
			throw new SamlException(e);
		}
	}

	protected NameIDPolicyType getNameIDPolicy(
		NameIdPolicy nameIdPolicy
	) {
		NameIDPolicyType result = null;
		if (nameIdPolicy != null) {
			result = new NameIDPolicyType();
			result.setAllowCreate(nameIdPolicy.getAllowCreate());
			try {
				result.setFormat(new URI(nameIdPolicy.getFormat().toString()));
			} catch (URISyntaxException e) {
				throw new SamlException(e);
			}
			result.setSPNameQualifier(nameIdPolicy.getSpNameQualifier());
		}
		return result;
	}

	protected RequestedAuthnContextType getRequestedAuthenticationContext(AuthenticationRequest request) {
		RequestedAuthnContextType result = null;
		if (request.getRequestedAuthenticationContext() != null) {
			result = new RequestedAuthnContextType();
			switch (request.getRequestedAuthenticationContext()) {
				case exact:
					result.setComparison(AuthnContextComparisonType.EXACT);
					break;
				case better:
					result.setComparison(AuthnContextComparisonType.BETTER);
					break;
				case maximum:
					result.setComparison(AuthnContextComparisonType.MAXIMUM);
					break;
				case minimum:
					result.setComparison(AuthnContextComparisonType.MINIMUM);
					break;
				default:
					result.setComparison(AuthnContextComparisonType.EXACT);
					break;
			}
			if (request.getAuthenticationContextClassReference() != null) {
				result.addAuthnContextClassRef(request.getAuthenticationContextClassReference().toString());
			}
		}
		return result;
	}

	protected NameIDType toIssuer(Issuer issuer) {
		NameIDType result = new NameIDType();
		result.setValue(issuer.getValue());
		try {
			if (issuer.getFormat() != null) {
				result.setFormat(new URI(issuer.getFormat().toString()));
			}
		} catch (URISyntaxException e) {
			throw new SamlException(e);
		}
		result.setSPNameQualifier(issuer.getSpNameQualifier());
		result.setNameQualifier(issuer.getNameQualifier());
		return result;
	}

	private IndexedEndpointType getIndexedEndpointType(Endpoint endpoint, int index) {
		try {
			IndexedEndpointType result = new IndexedEndpointType(
				new URI(endpoint.getBinding().toString()),
				new URI(endpoint.getLocation())
			);
			if (index > 0) {
				result.setIndex(index);
			}
			result.setIsDefault(endpoint.isDefault() ? true : null);
			return result;
		} catch (URISyntaxException e) {
			throw new SamlException(e);
		}
	}

	protected IndexedEndpointType getArtifactResolutionService(Endpoint ep, int i) {
		return getIndexedEndpointType(ep, i);
	}

	public IndexedEndpointType getSingleLogoutService(Endpoint endpoint) {
		return getIndexedEndpointType(endpoint, -1);
	}

	protected AttributeConsumingServiceType getAttributeConsumingService(List<Attribute> attributes) {
		AttributeConsumingServiceType service = new AttributeConsumingServiceType(0);
		service.setIsDefault(true);
		for (Attribute a : attributes) {
			RequestedAttributeType ra = new RequestedAttributeType(a.getName());
			ra.setIsRequired(a.isRequired());
			ra.setFriendlyName(a.getFriendlyName());
			ra.setName(a.getName());
			ra.setNameFormat(a.getNameFormat().toString());
			service.addRequestedAttribute(ra);
		}
		return service;
	}

	public IndexedEndpointType getSingleSignOnService(Endpoint endpoint, int index) {
		return getIndexedEndpointType(endpoint, index);
	}

	public KeyDescriptorType getKeyDescriptor(KeyData key) {
		KeyDescriptorType descriptor = new KeyDescriptorType();
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.newDocument();
			Element x509Cert = doc.createElementNS(NS_SIGNATURE, "ds:X509Certificate");
			x509Cert.setTextContent(X509Utilities.keyCleanup(key.getCertificate()));
			Element x509Data = doc.createElementNS(NS_SIGNATURE, "ds:X509Data");
			x509Data.appendChild(x509Cert);
			Element keyInfo = doc.createElementNS(NS_SIGNATURE, "ds:KeyInfo");
			keyInfo.appendChild(x509Data);
			descriptor.setKeyInfo(keyInfo);
			if (key.getType() != null) {
				switch (key.getType()) {
					case SIGNING:
						descriptor.setUse(KeyTypes.SIGNING);
						break;
					case ENCRYPTION:
						descriptor.setUse(KeyTypes.ENCRYPTION);
						break;
					case UNSPECIFIED:
						break;
				}
			}
			else {
				descriptor.setUse(KeyTypes.SIGNING);
			}
			return descriptor;
		} catch (SecurityException | ParserConfigurationException e) {
			throw new SamlKeyException(e);
		}
	}

	protected EncryptedAssertionType encryptAssertion(AssertionType assertion,
													  KeyData key,
													  KeyEncryptionMethod keyAlgorithm,
													  DataEncryptionMethod dataAlgorithm) {
//		Encrypter encrypter = getEncrypter(key, keyAlgorithm, dataAlgorithm);
//		try {
//			Encrypter.KeyPlacement keyPlacement =
//				Encrypter.KeyPlacement.valueOf(
//					System.getProperty("spring.security.saml.encrypt.key.placement", "PEER")
//				);
//			encrypter.setKeyPlacement(keyPlacement);
//			return encrypter.encrypt(assertion);
//		} catch (EncryptionException e) {
//			throw new SamlException("Unable to encrypt assertion.", e);
//		}
		throw new UnsupportedOperationException();
	}

	protected SamlObjectHolder parse(byte[] xml) {
		try {
			InputStream reader = new ByteArrayInputStream(xml);
			Document samlDocument = DocumentUtil.getDocument(reader);
			SAMLParser samlParser = SAMLParser.getInstance();
			JAXPValidationUtil.checkSchemaValidation(samlDocument);
			//check for signatures
			NodeList signature = samlDocument.getElementsByTagNameNS(NS_SIGNATURE, "Signature");
			if (signature != null && signature.getLength() > 0) {
				configureIdAttribute(samlDocument);
			}
			Object object = samlParser.parse(samlDocument);
			return new SamlObjectHolder(samlDocument, object);
		} catch (Exception e) {
			throw new SamlException(e);
		}
	}

	protected List<? extends Provider> getSsoProviders(EntityDescriptorType descriptor) {
		final List<SsoProvider> providers = new LinkedList<>();
		List<SSODescriptorType> roles = new LinkedList<>();
		descriptor.getChoiceType().stream()
			.forEach(ct -> ct.getDescriptors().stream().forEach(
				d -> {
					if (d.getIdpDescriptor() != null) {
						roles.add(d.getIdpDescriptor());
					}
					if (d.getSpDescriptor() != null) {
						roles.add(d.getSpDescriptor());
					}
				}
			));

		for (SSODescriptorType roleDescriptor : roles) {
			providers.add(getSsoProvider(roleDescriptor));
		}
		return providers;
	}

	protected SsoProvider getSsoProvider(SSODescriptorType descriptor) {
		if (descriptor instanceof SPSSODescriptorType) {
			SPSSODescriptorType desc = (SPSSODescriptorType) descriptor;
			ServiceProvider provider = new ServiceProvider();
			provider.setId(desc.getID());
			if (desc.getValidUntil() != null) {
				provider.setValidUntil(new DateTime(desc.getValidUntil().toGregorianCalendar()));
			}
			if (desc.getCacheDuration() != null) {
				provider.setCacheDuration(desc.getCacheDuration());
			}
			provider.setProtocolSupportEnumeration(desc.getProtocolSupportEnumeration());
			provider.setNameIds(getNameIDs(desc.getNameIDFormat()));
			provider.setArtifactResolutionService(getEndpoints(desc.getArtifactResolutionService()));
			provider.setSingleLogoutService(getEndpoints(desc.getSingleLogoutService()));
			provider.setManageNameIDService(getEndpoints(desc.getManageNameIDService()));
			provider.setAuthnRequestsSigned(desc.isAuthnRequestsSigned());
			provider.setWantAssertionsSigned(desc.isWantAssertionsSigned());
			provider.setAssertionConsumerService(getEndpoints(desc.getAssertionConsumerService()));
			provider.setRequestedAttributes(getRequestAttributes(desc));
			provider.setKeys(getProviderKeys(descriptor));
			provider.setDiscovery(getDiscovery(desc));
			provider.setRequestInitiation(getRequestInitiation(desc));
			//TODO
			//provider.setAttributeConsumingService(getEndpoints(desc.getAttributeConsumingServices()));
			return provider;
		}
		else if (descriptor instanceof IDPSSODescriptorType) {
			IDPSSODescriptorType desc = (IDPSSODescriptorType) descriptor;
			IdentityProvider provider = new IdentityProvider();
			provider.setId(desc.getID());
			if (desc.getValidUntil() != null) {
				provider.setValidUntil(new DateTime(desc.getValidUntil().toGregorianCalendar()));
			}
			if (desc.getCacheDuration() != null) {
				provider.setCacheDuration(desc.getCacheDuration());
			}
			provider.setProtocolSupportEnumeration(desc.getProtocolSupportEnumeration());
			provider.setNameIds(getNameIDs(desc.getNameIDFormat()));
			provider.setArtifactResolutionService(getEndpoints(desc.getArtifactResolutionService()));
			provider.setSingleLogoutService(getEndpoints(desc.getSingleLogoutService()));
			provider.setManageNameIDService(getEndpoints(desc.getManageNameIDService()));
			provider.setWantAuthnRequestsSigned(desc.isWantAuthnRequestsSigned());
			provider.setSingleSignOnService(getEndpoints(desc.getSingleSignOnService()));
			provider.setKeys(getProviderKeys(descriptor));
			provider.setDiscovery(getDiscovery(desc));
			provider.setRequestInitiation(getRequestInitiation(desc));
			return provider;
		}
		throw new UnsupportedOperationException(
			descriptor == null ?
				null :
				descriptor.getClass().getName()
		);
	}

	protected List<Attribute> getRequestAttributes(SPSSODescriptorType desc) {
		List<Attribute> result = new LinkedList<>();
		for (AttributeConsumingServiceType s : ofNullable(desc.getAttributeConsumingService()).orElse(emptyList())) {
			if (s != null) {
				//take the first one
				result.addAll(getRequestedAttributes(s.getRequestedAttribute()));
				break;
			}
		}
		return result;
	}

	protected Endpoint getRequestInitiation(RoleDescriptorType desc) {
		Endpoint result = null;
		if (desc.getExtensions() == null) {
			return null;
		}
		for (Object obj : desc.getExtensions().getAny()) {
			if (obj instanceof Element) {
				Element e = (Element) obj;
				if ("RequestInitiator".equals(e.getLocalName())) {
					String binding = e.getAttribute("Binding");
					String location = e.getAttribute("Location");
					String responseLocation = e.getAttribute("ResponseLocation");
					String index = e.getAttribute("index");
					String isDefault = e.getAttribute("isDefault");
					result = new Endpoint()
						.setIndex(hasText(index) ? Integer.valueOf(index) : 0)
						.setDefault(hasText(isDefault) ? Boolean.valueOf(isDefault) : false)
						.setBinding(hasText(binding) ? Binding.fromUrn(binding) : Binding.REQUEST_INITIATOR)
						.setLocation(location)
						.setResponseLocation(responseLocation);
				}
			}
		}
		return result;
	}

	protected Endpoint getDiscovery(RoleDescriptorType desc) {
		Endpoint result = null;
		if (desc.getExtensions() == null) {
			return null;
		}
		for (Object obj : desc.getExtensions().getAny()) {
			if (obj instanceof Element) {
				Element e = (Element) obj;
				if ("DiscoveryResponse".equals(e.getLocalName())) {
					String binding = e.getAttribute("Binding");
					String location = e.getAttribute("Location");
					String responseLocation = e.getAttribute("ResponseLocation");
					String index = e.getAttribute("index");
					String isDefault = e.getAttribute("isDefault");
					result = new Endpoint()
						.setIndex(hasText(index) ? Integer.valueOf(index) : 0)
						.setDefault(hasText(isDefault) ? Boolean.valueOf(isDefault) : false)
						.setBinding(hasText(binding) ? Binding.fromUrn(binding) : Binding.DISCOVERY)
						.setLocation(location)
						.setResponseLocation(responseLocation);
				}
			}
		}
		return result;
	}

	protected List<KeyData> getProviderKeys(SSODescriptorType descriptor) {
		List<KeyData> result = new LinkedList<>();
		for (KeyDescriptorType desc : ofNullable(descriptor.getKeyDescriptor()).orElse(emptyList())) {
			if (desc != null) {
				result.addAll(getKeyFromDescriptor(desc));
			}
		}
		return result;
	}

	protected List<KeyData> getKeyFromDescriptor(KeyDescriptorType desc) {
		List<KeyData> result = new LinkedList<>();
		if (desc.getKeyInfo() == null) {
			return null;
		}
		KeyType type = desc.getUse() != null ? KeyType.valueOf(desc.getUse().name()) : KeyType.UNSPECIFIED;
		int index = 0;
		result.add(
			new KeyData(
				type.getTypeName() + "-" + (index++),
				null,
				desc.getKeyInfo().getFirstChild().getTextContent(),
				null,
				type
			)
		);

		//for (X509DataType x509 : ofNullable(desc.getKeyInfo().getC).orElse(emptyList())) {
//			for (X509Certificate cert : ofNullable(x509.getX509Certificates()).orElse(emptyList())) {
//				result.add(new KeyData(type.getTypeName() + "-" + (index++), null, cert.getValue(), null,
//					type
//				));
//			}
//		}

		return result;
	}

	protected List<Endpoint> getEndpoints(List<? extends EndpointType> services) {
		List<Endpoint> result = new LinkedList<>();
		if (services != null) {
			services
				.stream()
				.forEach(s -> {
						Endpoint endpoint = new Endpoint()
							.setBinding(Binding.fromUrn(s.getBinding().toString()))
							.setLocation(s.getLocation().toString());
						if (s.getResponseLocation() != null) {
							endpoint.setResponseLocation(s.getResponseLocation().toString());
						}
						result.add(endpoint);
						if (s instanceof IndexedEndpointType) {
							IndexedEndpointType idxEndpoint = (IndexedEndpointType) s;
							endpoint
								.setIndex(idxEndpoint.getIndex())
								.setDefault(idxEndpoint.isIsDefault() != null ? idxEndpoint.isIsDefault() : false);
						}
					}
				);
		}
		return result;
	}

	protected List<NameId> getNameIDs(List<? extends Object> nameIDFormats) {
		List<NameId> result = new LinkedList<>();
		for (Object o : ofNullable(nameIDFormats).orElse(emptyList())) {
			if (o == null) {
				continue;
			}
			else if (o instanceof String) {
				result.add(NameId.fromUrn((String) o));
			}
			else if (o instanceof NameIDType) {
				NameIDType t = (NameIDType) o;
				result.add(NameId.fromUrn(t.getFormat().toString()));
			}
		}
		return result;
	}

	protected ResponseType internalToXml(Response response) {
//		org.opensaml.saml.saml2.core.Response result = buildSAMLObject(org.opensaml.saml.saml2.core.Response.class);
//		result.setConsent(response.getConsent());
//		result.setID(ofNullable(response.getId()).orElse("a" + UUID.randomUUID().toString()));
//		result.setInResponseTo(response.getInResponseTo());
//		result.setVersion(SAMLVersion.VERSION_20);
//		result.setIssueInstant(response.getIssueInstant());
//		result.setDestination(response.getDestination());
//		result.setIssuer(toIssuer(response.getIssuer()));
//
//		if (response.getStatus() == null || response.getStatus().getCode() == null) {
//			throw new SamlException("Status cannot be null on a response");
//		}
//		org.opensaml.saml.saml2.core.Status status = buildSAMLObject(org.opensaml.saml.saml2.core.Status.class);
//		org.opensaml.saml.saml2.core.StatusCode code = buildSAMLObject(org.opensaml.saml.saml2.core.StatusCode.class);
//		code.setValue(response.getStatus().getCode().toString());
//		status.setStatusCode(code);
//
//		if (hasText(response.getStatus().getMessage())) {
//			StatusMessage message = buildSAMLObject(StatusMessage.class);
//			message.setMessage(response.getStatus().getMessage());
//			status.setStatusMessage(message);
//		}
//
//		result.setStatus(status);
//
//		for (Assertion a : ofNullable(response.getAssertions()).orElse(emptyList())) {
//			org.opensaml.saml.saml2.core.Assertion osAssertion = internalToXml(a);
//			if (a.getEncryptionKey() != null) {
//				EncryptedAssertion encryptedAssertion =
//					encryptAssertion(osAssertion, a.getEncryptionKey(), a.getKeyAlgorithm(), a.getDataAlgorithm());
//				result.getEncryptedAssertions().add(encryptedAssertion);
//			}
//			else {
//				result.getAssertions().add(osAssertion);
//			}
//		}
//		if (response.getSigningKey() != null) {
//			signObject(result, response.getSigningKey(), response.getAlgorithm(), response.getDigest());
//		}
//		return result;
		throw new UnsupportedOperationException();
	}

	protected StatusResponseType internalToXml(LogoutResponse response) {
//		org.opensaml.saml.saml2.core.LogoutResponse result =
//			buildSAMLObject(org.opensaml.saml.saml2.core.LogoutResponse.class);
//		result.setInResponseTo(response.getInResponseTo());
//		result.setID(response.getId());
//		result.setIssueInstant(response.getIssueInstant());
//		result.setDestination(response.getDestination());
//
//		org.opensaml.saml.saml2.core.Issuer issuer = buildSAMLObject(org.opensaml.saml.saml2.core.Issuer.class);
//		issuer.setValue(response.getIssuer().getValue());
//		issuer.setNameQualifier(response.getIssuer().getNameQualifier());
//		issuer.setSPNameQualifier(response.getIssuer().getSpNameQualifier());
//		result.setIssuer(issuer);
//
//		org.opensaml.saml.saml2.core.Status status = buildSAMLObject(org.opensaml.saml.saml2.core.Status.class);
//		org.opensaml.saml.saml2.core.StatusCode code = buildSAMLObject(org.opensaml.saml.saml2.core.StatusCode.class);
//		code.setValue(response.getStatus().getCode().toString());
//		status.setStatusCode(code);
//		if (hasText(response.getStatus().getMessage())) {
//			StatusMessage message = buildSAMLObject(StatusMessage.class);
//			message.setMessage(response.getStatus().getMessage());
//			status.setStatusMessage(message);
//		}
//		result.setStatus(status);
//
//		if (response.getSigningKey() != null) {
//			this.signObject(result, response.getSigningKey(), response.getAlgorithm(), response.getDigest());
//		}
//
//		return result;
		throw new UnsupportedOperationException();
	}

	protected LogoutRequestType internalToXml(LogoutRequest request) {
//		org.opensaml.saml.saml2.core.LogoutRequest lr =
//			buildSAMLObject(org.opensaml.saml.saml2.core.LogoutRequest.class);
//		lr.setDestination(request.getDestination().getLocation());
//		lr.setID(request.getId());
//		lr.setVersion(SAMLVersion.VERSION_20);
//		org.opensaml.saml.saml2.core.Issuer issuer = buildSAMLObject(org.opensaml.saml.saml2.core.Issuer.class);
//		issuer.setValue(request.getIssuer().getValue());
//		issuer.setNameQualifier(request.getIssuer().getNameQualifier());
//		issuer.setSPNameQualifier(request.getIssuer().getSpNameQualifier());
//		lr.setIssuer(issuer);
//		lr.setIssueInstant(request.getIssueInstant());
//		lr.setNotOnOrAfter(request.getNotOnOrAfter());
//		NameID nameID = buildSAMLObject(NameID.class);
//		nameID.setFormat(request.getNameId().getFormat().toString());
//		nameID.setValue(request.getNameId().getValue());
//		nameID.setSPNameQualifier(request.getNameId().getSpNameQualifier());
//		nameID.setNameQualifier(request.getNameId().getNameQualifier());
//		lr.setNameID(nameID);
//		if (request.getSigningKey() != null) {
//			signObject(lr, request.getSigningKey(), request.getAlgorithm(), request.getDigest());
//		}
//		return lr;
		throw new UnsupportedOperationException();
	}

	protected AssertionType internalToXml(Assertion request) throws URISyntaxException {
		String id = ofNullable(request.getId()).orElse("A" + UUID.randomUUID().toString());
		XMLGregorianCalendar instant =
			getXmlGregorianCalendar(ofNullable(request.getIssueInstant()).orElse(DateTime.now()));
		AssertionType a = new AssertionType(id, instant);
		a.setIssuer(getIssuer(request.getIssuer()));
		a.setSubject(getSubject(request.getSubject()));
		a.setConditions(getConditions(request.getConditions()));

		for (AuthenticationStatement stmt : request.getAuthenticationStatements()) {
			AuthnStatementType authnStatement = getAuthnStatementType(stmt);
			a.addStatement(authnStatement);
		}

		for (Attribute attribute : request.getAttributes()) {
			AttributeStatementType ast = new AttributeStatementType();
			AttributeType at = new AttributeType(attribute.getName());
			at.setFriendlyName(attribute.getFriendlyName());
			at.setNameFormat(attribute.getNameFormat().toString());
			for (Object o : attribute.getValues()) {
				if (o != null) {
					at.addAttributeValue(o.toString());
				}
			}
			AttributeStatementType.ASTChoiceType choice = new AttributeStatementType.ASTChoiceType(at);
			ast.addAttribute(choice);
			a.addStatement(ast);
		}

		return a;
	}

	private AuthnStatementType getAuthnStatementType(AuthenticationStatement stmt) throws URISyntaxException {
		AuthnStatementType authnStatement = new AuthnStatementType(getXmlGregorianCalendar(stmt.getAuthInstant()));
		AuthnContextType actx = new AuthnContextType();
		if (stmt.getAuthenticationContext().getClassReference() != null) {
			AuthnContextClassRefType aref = new AuthnContextClassRefType(
				new URI(stmt.getAuthenticationContext().getClassReference().toString())
			);
			AuthnContextType.AuthnContextTypeSequence sequence = actx.new AuthnContextTypeSequence();
			sequence.setClassRef(aref);
			actx.setSequence(sequence);
		}

		authnStatement.setAuthnContext(actx);
		authnStatement.setSessionIndex(stmt.getSessionIndex());
		authnStatement.setSessionNotOnOrAfter(getXmlGregorianCalendar(stmt.getSessionNotOnOrAfter()));
		return authnStatement;
	}

	protected void addCondition(ConditionsType conditions, AssertionCondition c) {
//		if (c instanceof AudienceRestriction) {
//			org.opensaml.saml.saml2.core.AudienceRestriction ar =
//				buildSAMLObject(org.opensaml.saml.saml2.core.AudienceRestriction.class);
//			for (String audience : ((AudienceRestriction) c).getAudiences()) {
//				Audience aud = buildSAMLObject(Audience.class);
//				aud.setAudienceURI(audience);
//				ar.getAudiences().add(aud);
//			}
//			conditions.getAudienceRestrictions().add(ar);
//		}
//		else if (c instanceof OneTimeUse) {
//			org.opensaml.saml.saml2.core.OneTimeUse otu =
//				buildSAMLObject(org.opensaml.saml.saml2.core.OneTimeUse.class);
//			conditions.getConditions().add(otu);
//		}
		throw new UnsupportedOperationException();
	}

	protected NameIdPolicy fromNameIDPolicy(NameIDPolicyType nameIDPolicy) {
		NameIdPolicy result = null;
		if (nameIDPolicy != null) {
			result = new NameIdPolicy()
				.setAllowCreate(nameIDPolicy.isAllowCreate())
				.setFormat(NameId.fromUrn(nameIDPolicy.getFormat().toString()))
				.setSpNameQualifier(nameIDPolicy.getSPNameQualifier());
		}
		return result;
	}

	protected Response resolveResponse(
		ResponseType parsed,
		List<KeyData> verificationKeys,
		List<KeyData> localKeys
	) {
		Response result = new Response()
			.setConsent(parsed.getConsent())
			.setDestination(parsed.getDestination())
			.setId(parsed.getID())
			.setInResponseTo(parsed.getInResponseTo())
			.setIssueInstant(new DateTime(parsed.getIssueInstant().toGregorianCalendar()))
			.setIssuer(getIssuer(parsed.getIssuer()))
			.setVersion(parsed.getVersion())
			.setStatus(getStatus(parsed.getStatus()))
			.setAssertions(
				parsed.getAssertions().stream()
					.filter(a -> a.getAssertion() != null)
					.map(a -> resolveAssertion(a.getAssertion(), verificationKeys, localKeys, false)
					)
					.collect(Collectors.toList())
			);
		List<EncryptedAssertionType> encryptedAssertions = parsed.getAssertions().stream()
			.filter(a -> a.getEncryptedAssertion() != null)
			.map(a -> a.getEncryptedAssertion())
			.collect(Collectors.toList());
		if (!encryptedAssertions.isEmpty()) {
			encryptedAssertions
				.stream()
				.forEach(
					a -> result.addAssertion(
						resolveAssertion(
							(AssertionType) decrypt(a, localKeys),
							verificationKeys,
							localKeys,
							true
						)
					)
				);
		}

		return result;

	}

	private NameIDType getIssuer(Issuer issuer) {
		if (issuer == null) {
			return null;
		}
		NameIDType result = new NameIDType();
		result.setNameQualifier(issuer.getNameQualifier());
		result.setSPNameQualifier(issuer.getSpNameQualifier());
		result.setValue(issuer.getValue());
		try {
			result.setFormat(
				issuer.getFormat() == null ?
					null :
					new URI(issuer.getFormat().toString())
			);
		} catch (URISyntaxException e) {
			throw new SamlException(e);
		}
		return result;
	}

	private Issuer getIssuer(NameIDType issuer) {
		if (issuer == null) {
			return null;
		}
		Issuer result = new Issuer()
			.setValue(issuer.getValue())
			.setSpNameQualifier(issuer.getSPNameQualifier())
			.setNameQualifier(issuer.getNameQualifier());
		if (issuer.getFormat() != null) {
			result.setFormat(NameId.fromUrn(issuer.getFormat().toString()));
		}
		return result;
	}

	protected Status getStatus(StatusType status) {
		return new Status()
			.setCode(StatusCode.fromUrn(status.getStatusCode().getValue().toString()))
			.setMessage(status.getStatusMessage());
	}

	protected Assertion resolveAssertion(
		AssertionType parsed,
		List<KeyData> verificationKeys,
		List<KeyData> localKeys,
		boolean encrypted
	) {
		Signature signature = null;
		if (!encrypted) {
			throw new UnsupportedOperationException();
			//signature = validateSignature(parsed, verificationKeys);
		}
		return new Assertion(encrypted)
			.setSignature(signature)
			.setId(parsed.getID())
			.setIssueInstant(new DateTime(parsed.getIssueInstant().toGregorianCalendar()))
			.setVersion(parsed.getVersion())
			.setIssuer(getIssuer(parsed.getIssuer()))
			.setSubject(getSubject(parsed.getSubject(), localKeys))
			.setConditions(getConditions(parsed.getConditions()))
			.setAuthenticationStatements(getAuthenticationStatements(parsed.getStatements()))
			.setAttributes(getAttributes(parsed.getAttributeStatements(), localKeys))
			.setImplementation(parsed)
			;
	}

	protected Object decrypt(EncryptedElementType encrypted, List<KeyData> keys) {
//		DecryptionException last = null;
//		for (KeyData key : keys) {
//			Decrypter decrypter = getDecrypter(key);
//			try {
//				return (SAMLObject) decrypter.decryptData(encrypted.getEncryptedData());
//			} catch (DecryptionException e) {
//				logger.debug(format("Unable to decrypt element:%s", encrypted), e);
//				last = e;
//			}
//		}
//		if (last != null) {
//			throw new SamlKeyException("Unable to decrypt object.", last);
//		}
		return null;
	}

	private SubjectType getSubject(Subject subject) {
		try {
			if (subject == null) {
				return null;
			}

			NameIDType principal = new NameIDType();
			principal.setValue(subject.getPrincipal().getValue());
			principal.setSPProvidedID(subject.getPrincipal().getSpProvidedId());
			principal.setSPNameQualifier(subject.getPrincipal().getSpNameQualifier());
			principal.setNameQualifier(subject.getPrincipal().getNameQualifier());
			principal.setFormat(new URI(subject.getPrincipal().getFormat().toString()));

			SubjectType.STSubType subType = new SubjectType.STSubType();
			subType.addBaseID(principal);

			for (SubjectConfirmation confirmation : subject.getConfirmations()) {
				SubjectConfirmationType ct = new SubjectConfirmationType();
				ct.setMethod(confirmation.getMethod().toString());
				if (confirmation.getNameId() != null) {
					NameIDType nameId = new NameIDType();
					nameId.setValue(confirmation.getNameId());
					if (confirmation.getFormat() != null) {
						nameId.setFormat(new URI(confirmation.getFormat().toString()));
					}
					ct.setNameID(nameId);
					SubjectConfirmationData confirmationData = confirmation.getConfirmationData();
					if (confirmationData != null) {
						SubjectConfirmationDataType cdataType = new SubjectConfirmationDataType();
						cdataType.setInResponseTo(confirmationData.getInResponseTo());
						cdataType.setNotBefore(getXmlGregorianCalendar(confirmationData.getNotBefore()));
						cdataType.setNotOnOrAfter(getXmlGregorianCalendar(confirmationData.getNotOnOrAfter()));
						cdataType.setRecipient(confirmationData.getRecipient());
						ct.setSubjectConfirmationData(cdataType);
					}
				}
				subType.addConfirmation(ct);
			}

			SubjectType result = new SubjectType();
			result.setSubType(subType);
			return result;
		} catch (URISyntaxException | NullPointerException e) {
			throw new SamlException(e);
		}
	}

	private Subject getSubject(SubjectType subject, List<KeyData> localKeys) {

		return new Subject()
			.setPrincipal(getPrincipal(subject, localKeys))
			.setConfirmations(getConfirmations(subject.getConfirmation(), localKeys))
			;
	}

	private ConditionsType getConditions(Conditions conditions) {
		ConditionsType result = new ConditionsType();
		result.setNotBefore(getXmlGregorianCalendar(conditions.getNotBefore()));
		result.setNotOnOrAfter(getXmlGregorianCalendar(conditions.getNotOnOrAfter()));
		getCriteriaOut(conditions.getCriteria()).forEach(
			c -> result.addCondition(c)
		);
		return result;
	}

	private Conditions getConditions(ConditionsType conditions) {
		return new Conditions()
			.setNotBefore(new DateTime(conditions.getNotBefore().toGregorianCalendar()))
			.setNotOnOrAfter(new DateTime(conditions.getNotOnOrAfter().toGregorianCalendar()))
			.setCriteria(getCriteria(conditions.getConditions()));
	}

	protected List<AuthenticationStatement> getAuthenticationStatements(Collection<StatementAbstractType> authnStatements) {
		List<AuthenticationStatement> result = new LinkedList<>();

		for (StatementAbstractType st : ofNullable(authnStatements).orElse(emptyList())) {
			if (st instanceof AuthnStatementType) {
				AuthnStatementType s = (AuthnStatementType) st;
				AuthnContextType authnContext = s.getAuthnContext();
				AuthnContextClassRefType authnContextClassRef =
					(AuthnContextClassRefType) authnContext.getURIType().stream()
						.filter(t -> t instanceof AuthnContextClassRefType)
						.findFirst()
						.orElse(null);
				String ref = null;
				if (authnContextClassRef.getValue() != null) {
					ref = authnContextClassRef.getValue().toString();
				}

				result.add(
					new AuthenticationStatement()
						.setSessionIndex(s.getSessionIndex())
						.setAuthInstant(new DateTime(s.getAuthnInstant().toGregorianCalendar()))
						.setSessionNotOnOrAfter(new DateTime(s.getSessionNotOnOrAfter().toGregorianCalendar()))
						.setAuthenticationContext(
							authnContext != null ?
								new AuthenticationContext()
									.setClassReference(AuthenticationContextClassReference.fromUrn(ref))
								: null
						)
				);
			}

		}
		return result;
	}

	protected List<Attribute> getAttributes(
		Collection<AttributeStatementType> attributeStatements, List<KeyData>
		localKeys
	) {
		List<Attribute> result = new LinkedList<>();
		for (AttributeStatementType stmt : ofNullable(attributeStatements).orElse(emptyList())) {
			for (AttributeStatementType.ASTChoiceType a : ofNullable(stmt.getAttributes()).orElse(emptyList())) {
				if (a.getAttribute() != null) {
					result.add(
						new Attribute()
							.setFriendlyName(a.getAttribute().getFriendlyName())
							.setName(a.getAttribute().getName())
							.setNameFormat(AttributeNameFormat.fromUrn(a.getAttribute().getNameFormat()))
							.setValues(getJavaValues(a.getAttribute().getAttributeValue()))
					);
				}
				else if (a.getEncryptedAssertion() != null) {
					AttributeType at = (AttributeType) decrypt(a.getEncryptedAssertion(), localKeys);
					result.add(
						new Attribute()
							.setFriendlyName(at.getFriendlyName())
							.setName(at.getName())
							.setNameFormat(AttributeNameFormat.fromUrn(at.getNameFormat()))
							.setValues(getJavaValues(at.getAttributeValue()))
					);
				}
			}
		}
		return result;
	}

	protected NameIdPrincipal getPrincipal(SubjectType subject, List<KeyData> localKeys) {
		NameIDType p = null;
//			getNameID(
//				subject.getNameID(),
//				subject.getEncryptedID(),
//				localKeys
//			);
//		if (p != null) {
//			return getNameIdPrincipal(p);
//		}
//		else {
		throw new UnsupportedOperationException("Currently only supporting NameID subject principals");
//		}
	}

	protected List<SubjectConfirmation> getConfirmations(
		List<SubjectConfirmationType> subjectConfirmations, List<KeyData> localKeys
	) {
		List<SubjectConfirmation> result = new LinkedList<>();
		for (SubjectConfirmationType s : subjectConfirmations) {
			NameIDType nameID = getNameID(s.getNameID(), s.getEncryptedID(), localKeys);
			result.add(
				new SubjectConfirmation()
					.setNameId(nameID != null ? nameID.getValue() : null)
					.setFormat(nameID != null ? NameId.fromUrn(nameID.getFormat().toString()) : null)
					.setMethod(SubjectConfirmationMethod.fromUrn(s.getMethod()))
					.setConfirmationData(
						new SubjectConfirmationData()
							.setRecipient(s.getSubjectConfirmationData().getRecipient())
							.setNotOnOrAfter(new DateTime(s.getSubjectConfirmationData()
								.getNotOnOrAfter()
								.toGregorianCalendar()))
							.setNotBefore(new DateTime(s.getSubjectConfirmationData().getNotBefore()))
							.setInResponseTo(s.getSubjectConfirmationData().getInResponseTo())
					)
			);
		}
		return result;
	}

	private List<ConditionAbstractType> getCriteriaOut(List<AssertionCondition> conditions) {
		List<ConditionAbstractType> result = new LinkedList<>();
		ofNullable(conditions).orElse(emptyList()).forEach(
			c -> {
				if (c instanceof AudienceRestriction) {
					AudienceRestrictionType a = new AudienceRestrictionType();
					AudienceRestriction ar = (AudienceRestriction)c;
					for (String s : ofNullable(ar.getAudiences()).orElse(emptyList())) {
						try {
							a.addAudience(new URI(s));
						} catch (URISyntaxException e) {
							throw new SamlException(e);
						}
					}
					result.add(a);
				}
				else if (c instanceof OneTimeUse) {
					OneTimeUseType one = new OneTimeUseType();
					result.add(one);
				}
			}

		);
		return result;
	}
	private List<AssertionCondition> getCriteria(List<ConditionAbstractType> conditions) {
		List<AssertionCondition> result = new LinkedList<>();
		for (ConditionAbstractType c : conditions) {
			if (c instanceof AudienceRestrictionType) {
				AudienceRestrictionType aud = (AudienceRestrictionType) c;

				if (aud.getAudience() != null) {
					result.add(
						new AudienceRestriction()
							.setAudiences(
								aud.getAudience().stream().map(
									a -> a.toString()
								).collect(Collectors.toList())
							)
					);
				}
			}
			else if (c instanceof OneTimeUseType) {
				result.add(new OneTimeUse());
			}
		}
		return result;
	}

	protected List<Object> getJavaValues(List<Object> attributeValues) {
		List<Object> result = new LinkedList<>(attributeValues);
		return result;
	}

	protected NameIDType getNameID(NameIDType id,
								   EncryptedElementType eid,
								   List<KeyData> localKeys) {
		NameIDType result = id;
		if (result == null && eid != null && eid.getEncryptedElement() != null) {
			//result = (NameIDType) decrypt(eid, localKeys);
			throw new UnsupportedOperationException();
		}
		return result;
	}

	protected LogoutResponse resolveLogoutResponse(StatusResponseType response,
												   List<KeyData> verificationKeys,
												   List<KeyData> localKeys) {
		LogoutResponse result = new LogoutResponse()
			.setId(response.getID())
			.setInResponseTo(response.getInResponseTo())
			.setConsent(response.getConsent())
			.setVersion(response.getVersion())
			.setIssueInstant(new DateTime(response.getIssueInstant().toGregorianCalendar()))
			.setIssuer(getIssuer(response.getIssuer()))
			.setDestination(response.getDestination())
			.setStatus(getStatus(response.getStatus()));

		return result;
	}

	protected LogoutRequest resolveLogoutRequest(LogoutRequestType request,
												 List<KeyData> verificationKeys,
												 List<KeyData> localKeys) {
		LogoutRequest result = new LogoutRequest()
			.setId(request.getID())
			.setConsent(request.getConsent())
			.setVersion(request.getVersion().toString())
			.setNotOnOrAfter(new DateTime(request.getNotOnOrAfter()))
			.setIssueInstant(new DateTime(request.getIssueInstant()))
			.setReason(LogoutReason.fromUrn(request.getReason()))
			.setIssuer(getIssuer(request.getIssuer()))
			.setDestination(new Endpoint().setLocation(request.getDestination().toString()));
		NameIDType nameID = getNameID(request.getNameID(), request.getEncryptedID(), localKeys);
		result.setNameId(getNameIdPrincipal(nameID));
		return result;
	}

	protected NameIdPrincipal getNameIdPrincipal(NameIDType p) {
		return new NameIdPrincipal()
			.setSpNameQualifier(p.getSPNameQualifier())
			.setNameQualifier(p.getNameQualifier())
			.setFormat(NameId.fromUrn(p.getFormat().toString()))
			.setSpProvidedId(p.getSPProvidedID())
			.setValue(p.getValue());
	}

	protected List<Attribute> getRequestedAttributes(List<RequestedAttributeType> attributes) {
		List<Attribute> result = new LinkedList<>();
		for (RequestedAttributeType a : ofNullable(attributes).orElse(emptyList())) {
			result.add(
				new Attribute()
					.setFriendlyName(a.getFriendlyName())
					.setName(a.getName())
					.setNameFormat(AttributeNameFormat.fromUrn(a.getNameFormat()))
					.setValues(getJavaValues(a.getAttributeValue()))
					.setRequired(a.isIsRequired())
			);
		}
		return result;
	}

	protected AuthenticationRequest resolveAuthenticationRequest(AuthnRequestType parsed,
																 Map<String, Signature> signatureMap) {
		AuthnRequestType request = parsed;
		AuthenticationRequest result = new AuthenticationRequest()
			.setBinding(Binding.fromUrn(request.getProtocolBinding().toString()))
			.setAssertionConsumerService(
				getEndpoint(
					request.getAssertionConsumerServiceURL().toString(),
					Binding.fromUrn(request.getProtocolBinding().toString()),
					ofNullable(request.getAssertionConsumerServiceIndex()).orElse(-1),
					false
				)
			)
			.setDestination(
				getEndpoint(
					request.getDestination().toString(),
					Binding.fromUrn(request.getProtocolBinding().toString()),
					-1,
					false
				)
			)
			.setIssuer(getIssuer(request.getIssuer()))
			.setForceAuth(request.isForceAuthn())
			.setPassive(request.isIsPassive())
			.setId(request.getID())
			.setIssueInstant(new DateTime(request.getIssueInstant().toGregorianCalendar()))
			.setVersion(request.getVersion())
			.setRequestedAuthenticationContext(getRequestedAuthenticationContext(request))
			.setAuthenticationContextClassReference(getAuthenticationContextClassReference(request))
			.setNameIdPolicy(fromNameIDPolicy(request.getNameIDPolicy()));
		KeycloakSignatureValidator.assignSignatureToObject(signatureMap, result, request.getSignature());
		return result;
	}

	protected AuthenticationContextClassReference getAuthenticationContextClassReference(AuthnRequestType request) {
		AuthenticationContextClassReference result = null;
		final RequestedAuthnContextType context = request.getRequestedAuthnContext();
		if (context != null && !CollectionUtils.isEmpty(context.getAuthnContextClassRef())) {
			final String urn = context.getAuthnContextClassRef().get(0);
			result = AuthenticationContextClassReference.fromUrn(urn);
		}
		return result;
	}

	protected RequestedAuthenticationContext getRequestedAuthenticationContext(AuthnRequestType request) {
		RequestedAuthenticationContext result = null;

		if (request.getRequestedAuthnContext() != null) {
			AuthnContextComparisonType comparison = request.getRequestedAuthnContext().getComparison();
			if (null != comparison) {
				result = RequestedAuthenticationContext.fromName(comparison.toString());
			}
		}
		return result;
	}

	protected Metadata resolveMetadata(EntitiesDescriptorType parsed,
									   Map<String, Signature> signatureMap) {
		Metadata result = null, current = null;
		for (Object object : parsed.getEntityDescriptor()) {
			EntityDescriptorType desc = (EntityDescriptorType) object;
			if (result == null) {
				result = resolveMetadata(desc, signatureMap);
				current = result;
			}
			else {
				Metadata m = resolveMetadata(desc, signatureMap);
				current.setNext(m);
				current = m;
			}
		}
		return result;
	}

	protected Metadata resolveMetadata(EntityDescriptorType parsed,
									   Map<String, Signature> signatureMap) {
		EntityDescriptorType descriptor = parsed;
		List<? extends Provider> ssoProviders = getSsoProviders(descriptor);
		Metadata desc = getMetadata(ssoProviders);
		long duration = descriptor.getCacheDuration() != null ?
			descriptor.getCacheDuration().getTimeInMillis(new Date()) : -1;

		desc.setCacheDuration(toDuration(duration));
		desc.setEntityId(descriptor.getEntityID());
		if (isUrl(desc.getEntityId())) {
			desc.setEntityAlias(getHostFromUrl(desc.getEntityId()));
		}
		else {
			desc.setEntityAlias(desc.getEntityId());
		}

		desc.setId(descriptor.getID());
		if (ofNullable(descriptor.getValidUntil()).isPresent()) {
			desc.setValidUntil(new DateTime(descriptor.getValidUntil().toGregorianCalendar()));
		}

		KeycloakSignatureValidator.assignSignatureToObject(signatureMap, desc, descriptor.getSignature());
		return desc;
	}

	protected Metadata getMetadata(List<? extends Provider> ssoProviders) {
		Metadata result = determineMetadataType(ssoProviders);
		result.setProviders(ssoProviders);
		return result;
	}

	protected XMLObject objectToXmlObject(Object o) {
		if (o == null) {
			return null;
		}
//		else if (o instanceof String) {
//			XSStringBuilder builder = (XSStringBuilder) getBuilderFactory().getBuilder(XSString.TYPE_NAME);
//			XSString s = builder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
//			s.setValue((String) o);
//			return s;
//		}
//		else if (o instanceof URI || o instanceof URL) {
//			XSURIBuilder builder = (XSURIBuilder) getBuilderFactory().getBuilder(XSURI.TYPE_NAME);
//			XSURI uri = builder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSURI.TYPE_NAME);
//			uri.setValue(o.toString());
//			return uri;
//		}
//		else if (o instanceof Boolean) {
//			XSBooleanBuilder builder = (XSBooleanBuilder) getBuilderFactory().getBuilder(XSBoolean.TYPE_NAME);
//			XSBoolean b = builder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSBoolean.TYPE_NAME);
//			XSBooleanValue v = XSBooleanValue.valueOf(o.toString());
//			b.setValue(v);
//			return b;
//		}
//		else if (o instanceof DateTime) {
//			XSDateTimeBuilder builder = (XSDateTimeBuilder) getBuilderFactory().getBuilder(XSDateTime.TYPE_NAME);
//			XSDateTime dt = builder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSDateTime.TYPE_NAME);
//			dt.setValue((DateTime) o);
//			return dt;
//		}
//		else if (o instanceof Integer) {
//			XSIntegerBuilder builder = (XSIntegerBuilder) getBuilderFactory().getBuilder(XSInteger.TYPE_NAME);
//			XSInteger i = builder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSInteger.TYPE_NAME);
//			i.setValue(((Integer) o).intValue());
//			return i;
//		}
//		else {
//			XSAnyBuilder builder = (XSAnyBuilder) getBuilderFactory().getBuilder(XSAny.TYPE_NAME);
//			XSAny any = builder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSAny.TYPE_NAME);
//			any.setTextContent(o.toString());
//			return any;
//		}
		throw new UnsupportedOperationException();
	}

	protected String xmlObjectToString(XMLObject o) {
		String toMatch = null;
//		if (o instanceof XSString) {
//			toMatch = ((XSString) o).getValue();
//		}
//		else if (o instanceof XSURI) {
//			toMatch = ((XSURI) o).getValue();
//		}
//		else if (o instanceof XSBoolean) {
//			toMatch = ((XSBoolean) o).getValue().getValue() ? "1" : "0";
//		}
//		else if (o instanceof XSInteger) {
//			toMatch = ((XSInteger) o).getValue().toString();
//		}
//		else if (o instanceof XSDateTime) {
//			final DateTime dt = ((XSDateTime) o).getValue();
//			if (dt != null) {
//				toMatch = ((XSDateTime) o).getDateTimeFormatter().print(dt);
//			}
//		}
//		else if (o instanceof XSBase64Binary) {
//			toMatch = ((XSBase64Binary) o).getValue();
//		}
//		else if (o instanceof XSAny) {
//			final XSAny wc = (XSAny) o;
//			if (wc.getUnknownAttributes().isEmpty() && wc.getUnknownXMLObjects().isEmpty()) {
//				toMatch = wc.getTextContent();
//			}
//		}
//		if (toMatch != null) {
//			return toMatch;
//		}
		throw new UnsupportedOperationException();
//		return null;
	}

	protected Endpoint getEndpoint(String url, Binding binding, int index, boolean isDefault) {
		return
			new Endpoint()
				.setIndex(index)
				.setBinding(binding)
				.setLocation(url)
				.setDefault(isDefault)
				.setIndex(index);
	}

	public URI getNameIDFormat(NameId nameId) {
		try {
			return new URI(nameId.toString());
		} catch (URISyntaxException e) {
			throw new SamlException(e);
		}
	}

	public IndexedEndpointType getAssertionConsumerService(Endpoint endpoint, int index) {
		return getIndexedEndpointType(endpoint, index);
	}
}
