package com.evolveum.midpoint.provisioning.test.ucf;

import static org.junit.Assert.*;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Response.Status.Family;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;

import org.identityconnectors.framework.common.objects.Uid;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.evolveum.midpoint.common.result.OperationResult;

import com.evolveum.midpoint.provisioning.schema.ResourceSchema;
import com.evolveum.midpoint.provisioning.schema.util.ObjectValueWriter;
import com.evolveum.midpoint.provisioning.ucf.api.CommunicationException;
import com.evolveum.midpoint.provisioning.ucf.api.ConnectorInstance;
import com.evolveum.midpoint.provisioning.ucf.api.ConnectorManager;
import com.evolveum.midpoint.provisioning.ucf.api.GenericFrameworkException;
import com.evolveum.midpoint.provisioning.ucf.api.ObjectNotFoundException;
import com.evolveum.midpoint.provisioning.ucf.api.Operation;
import com.evolveum.midpoint.provisioning.ucf.impl.ConnectorManagerIcfImpl;
import com.evolveum.midpoint.schema.processor.Property;
import com.evolveum.midpoint.schema.processor.PropertyContainer;
import com.evolveum.midpoint.schema.processor.PropertyContainerDefinition;
import com.evolveum.midpoint.schema.processor.PropertyDefinition;
import com.evolveum.midpoint.schema.processor.ResourceObject;
import com.evolveum.midpoint.schema.processor.ResourceObjectAttribute;
import com.evolveum.midpoint.schema.processor.ResourceObjectAttributeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceObjectDefinition;
import com.evolveum.midpoint.schema.processor.Schema;
import com.evolveum.midpoint.test.ldap.OpenDJUnitTestAdapter;
import com.evolveum.midpoint.test.ldap.OpenDJUtil;
import com.evolveum.midpoint.test.repository.BaseXDatabaseFactory;
import com.evolveum.midpoint.xml.ns._public.common.common_1.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectFactory;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowType;

import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceType;
import com.evolveum.midpoint.xml.schema.SchemaConstants;

public class AddDeleteObjectUcfTest extends OpenDJUnitTestAdapter {

	private static final String FILENAME_RESOURCE_OPENDJ = "src/test/resources/ucf/opendj-resource.xml";
	private static final String FILENAME_RESOURCE_OPENDJ_BAD = "src/test/resources/ucf/opendj-resource-bad.xml";

	private static final String RESOURCE_NS = "http://midpoint.evolveum.com/xml/ns/public/resource/instances/ef2bc95b-76e0-48e2-86d6-3d4f02d3e1a2";

	protected static OpenDJUtil djUtil = new OpenDJUtil();
	private JAXBContext jaxbctx;
	ResourceType resource;
	ResourceType badResource;
	private ConnectorManager manager;
	private ConnectorInstance cc;
	Schema schema;

	public AddDeleteObjectUcfTest() throws JAXBException {
		jaxbctx = JAXBContext.newInstance(ObjectFactory.class.getPackage()
				.getName());
	}

	@BeforeClass
	public static void startLdap() throws Exception {
		startACleanDJ();
	}

	@AfterClass
	public static void stopLdap() throws Exception {
		stopDJ();
	}

	@Before
	public void initUcf() throws Exception {

		File file = new File(FILENAME_RESOURCE_OPENDJ);
		FileInputStream fis = new FileInputStream(file);

		Unmarshaller u = jaxbctx.createUnmarshaller();
		Object object = u.unmarshal(fis);
		resource = (ResourceType) ((JAXBElement) object).getValue();

		// Second copy for negative test cases
		file = new File(FILENAME_RESOURCE_OPENDJ_BAD);
		fis = new FileInputStream(file);
		object = u.unmarshal(fis);
		badResource = (ResourceType) ((JAXBElement) object).getValue();

		ConnectorManagerIcfImpl managerImpl = new ConnectorManagerIcfImpl();
		managerImpl.initialize();
		manager = managerImpl;

		cc = manager.createConnectorInstance(resource);

		assertNotNull(cc);

		OperationResult result = new OperationResult(this.getClass().getName()
				+ ".initUcf");
		schema = cc.fetchResourceSchema(result);

		assertNotNull(schema);

	}

	@After
	public void shutdownUcf() throws Exception {
		BaseXDatabaseFactory.XMLServerStop();
	}

	private Set<ResourceObjectAttribute> addSampleResourceObject(String name,
			String givenName, String familyName) throws CommunicationException,
			GenericFrameworkException {
		OperationResult result = new OperationResult(this.getClass().getName()
				+ ".testAdd");

		ResourceObjectDefinition accountDefinition = (ResourceObjectDefinition) schema
				.findContainerDefinitionByType(new QName(resource
						.getNamespace(), "AccountObjectClass"));
		System.out.println(accountDefinition.getTypeName().getNamespaceURI());
		ResourceObject resourceObject = accountDefinition.instantiate();
		resourceObject.getProperties();

		PropertyDefinition propertyDefinition = accountDefinition
				.findPropertyDefinition(SchemaConstants.ICFS_NAME);
		Property property = propertyDefinition.instantiate();
		property.setValue("uid=" + name + ",ou=people,dc=example,dc=com");
		resourceObject.getProperties().add(property);

		propertyDefinition = accountDefinition
				.findPropertyDefinition(new QName(RESOURCE_NS, "sn"));
		property = propertyDefinition.instantiate();
		property.setValue(familyName);
		resourceObject.getProperties().add(property);

		propertyDefinition = accountDefinition
				.findPropertyDefinition(new QName(RESOURCE_NS, "cn"));
		property = propertyDefinition.instantiate();
		property.setValue(givenName + " " + familyName);
		resourceObject.getProperties().add(property);

		propertyDefinition = accountDefinition
				.findPropertyDefinition(new QName(RESOURCE_NS, "givenName"));
		property = propertyDefinition.instantiate();
		property.setValue(givenName);
		resourceObject.getProperties().add(property);

		Set<Operation> operation = new HashSet<Operation>();
		Set<ResourceObjectAttribute> resourceAttributes = cc.addObject(
				resourceObject, operation, result);
		return resourceAttributes;
	}

	@Test
	public void testAddObject() throws Exception {

		OperationResult result = new OperationResult(this.getClass().getName()
				+ ".testAdd");

		Set<ResourceObjectAttribute> resourceAttributes = addSampleResourceObject(
				"jack", "Jack", "Sparow");
		// Set<Operation> operation = new HashSet<Operation>();
		// Set<ResourceObjectAttribute> resourceAttributes =
		// cc.addObject(resourceObject, operation, result);
		for (ResourceObjectAttribute resourceAttribute : resourceAttributes) {
			if (SchemaConstants.ICFS_UID.equals(resourceAttribute.getName())) {
				String uid = resourceAttribute.getValue(String.class);
				System.out.println("uuuuid:" + uid);
				assertNotNull(uid);

			}
		}

	}

	@Test
	public void testDeleteObject() throws Exception {

		OperationResult result = new OperationResult(this.getClass().getName()
				+ ".testDelete");

		Set<ResourceObjectAttribute> identifiers = addSampleResourceObject(
				"john", "John", "Smith");

		String uid = null;
		for (ResourceObjectAttribute resourceAttribute : identifiers) {
			if (SchemaConstants.ICFS_UID.equals(resourceAttribute.getName())) {
				uid = resourceAttribute.getValue(String.class);
				System.out.println("uuuuid:" + uid);
				assertNotNull(uid);
			}
		}

		QName objectClass = new QName(resource.getNamespace(),
				"AccountObjectClass");

		cc.deleteObject(objectClass, identifiers, result);

		ResourceObject resObj = null;
		try {
			 resObj = cc.fetchObject(objectClass, identifiers, result);
			fail();
		} catch (ObjectNotFoundException ex) {
			assertNull(resObj);
		}

	}

}
