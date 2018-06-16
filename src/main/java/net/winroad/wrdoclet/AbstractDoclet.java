package net.winroad.wrdoclet;

import net.winroad.wrdoclet.OASV3.*;
import net.winroad.wrdoclet.data.*;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.LanguageVersion;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.RootDoc;
import com.sun.tools.doclets.internal.toolkit.Configuration;
import com.sun.tools.doclets.internal.toolkit.builders.AbstractBuilder;
import com.sun.tools.doclets.internal.toolkit.builders.BuilderFactory;
import com.sun.tools.doclets.internal.toolkit.util.ClassTree;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public abstract class AbstractDoclet {

	public AbstractDoclet() {
	}

	/**
	 * The global configuration information for this run.
	 */
	public Configuration configuration;

	/**
	 * The method that starts the execution of the doclet.
	 * 
	 * @param doclet
	 *            the doclet to start the execution for.
	 * @param root
	 *            the {@link com.sun.javadoc.RootDoc} that points to the source
	 *            to document.
	 * @return true if the doclet executed without error. False otherwise.
	 */
	public boolean start(AbstractDoclet doclet, RootDoc root) {
		configuration = configuration();
		configuration.root = root;
		try {
			doclet.startGeneration(root);
		} catch (Exception exc) {
			exc.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * Indicate that this doclet supports the 1.5 language features.
	 * 
	 * @return JAVA_1_5, indicating that the new features are supported.
	 */
	public static LanguageVersion languageVersion() {
		return LanguageVersion.JAVA_1_5;
	}

	/**
	 * Create the configuration instance and returns it.
	 * 
	 * @return the configuration of the doclet.
	 */
	public abstract Configuration configuration();

	/**
	 * Start the generation of files. Call generate methods in the individual
	 * writers, which will in turn genrate the documentation files. Call the
	 * TreeWriter generation first to ensure the Class Hierarchy is built first
	 * and then can be used in the later generation.
	 * 
	 * @see com.sun.javadoc.RootDoc
	 */
	private void startGeneration(RootDoc root) throws Exception {
		if (root.classes().length == 0) {
			configuration.message.error("doclet.No_Public_Classes_To_Document");
			return;
		}
		configuration.setOptions();
		configuration.getDocletSpecificMsg().notice("doclet.build_version",
				configuration.getDocletSpecificBuildDate());

		WRDoc wrDoc = new WRDoc(configuration);

		generateWRDocFiles(root, wrDoc);

		configuration.tagletManager.printReport();
	}

	public abstract void generateWRDocFiles(RootDoc root, WRDoc wrDoc)
			throws Exception;

	public abstract void resetConfiguration();
	
	/**
	 * Generate additional documentation that is added to the API documentation.
	 * 
	 * @param root
	 *            the RootDoc of source to document.
	 * @param classtree
	 *            the data structure representing the class tree.
	 * @throws Exception builderFactory may throw exception
	 */
	protected void generateOtherFiles(RootDoc root, ClassTree classtree)
			throws Exception {
		BuilderFactory builderFactory = configuration.getBuilderFactory();
		AbstractBuilder constantsSummaryBuilder = builderFactory
				.getConstantsSummaryBuider();
		constantsSummaryBuilder.build();
		AbstractBuilder serializedFormBuilder = builderFactory
				.getSerializedFormBuilder();
		serializedFormBuilder.build();
	}

	/**
	 * Generate the package documentation.
	 * 
	 * @param classtree
	 *            the data structure representing the class tree.
	 * @throws Exception blablabla
	 */
	protected abstract void generatePackageFiles(ClassTree classtree)
			throws Exception;

	/**
	 * Generate the class documentation.
	 * 
	 * @param classtree
	 *            the data structure representing the class tree.
	 * @param arr 
	 * 				classdocs.
	 */
	protected abstract void generateClassFiles(ClassDoc[] arr,
			ClassTree classtree);

	/**
	 * Iterate through all classes and construct documentation for them.
	 * 
	 * @param root
	 *            the RootDoc of source to document.
	 * @param classtree
	 *            the data structure representing the class tree.
	 */
	protected void generateClassFiles(RootDoc root, ClassTree classtree) {
		generateClassFiles(classtree);
		PackageDoc[] packages = root.specifiedPackages();
		for (int i = 0; i < packages.length; i++) {
			generateClassFiles(packages[i].allClasses(), classtree);
		}
	}

	protected OASV3 convertToOAS(OpenAPI openAPI, String version) {
		if(StringUtils.isEmpty(openAPI.getRequestMapping().getMethodType())) {
			return null;
		}
		OASV3 OASV3 = new OASV3();
		HashMap<String, Schema> componentSchemas = new HashMap<>();
		Components components = new Components();
		components.setSchemas(componentSchemas);
		OASV3.setComponents(components);
		processOASInfo(openAPI, version, OASV3);
		processPaths(openAPI, OASV3, componentSchemas);
		processTags(openAPI, OASV3);
		return OASV3;
	}

	private void processTags(OpenAPI openAPI, OASV3 OASV3) {
		List<Tag> tags = new LinkedList<>();
		for(String tag : openAPI.getTags()) {
			Tag tagObj = new Tag();
			tagObj.setName(tag);
			tags.add(tagObj);
		}
		OASV3.setTags(tags);
	}

	private RequestBody convertRequestBody(APIParameter apiParameter, HashMap<String, Schema> componentSchemas) {
		RequestBody requestBody = new RequestBody();
		requestBody.setDescription(apiParameter.getDescription());
		requestBody.setRequired(ParameterOccurs.REQUIRED.equals(apiParameter.getParameterOccurs()));
		HashMap<String, MediaType> content = new HashMap<>();
		MediaType mediaType = new MediaType();
		content.put("application/json", mediaType);
		Schema schema = new Schema();
		mediaType.setSchema(schema);
		if(setSchemaType(apiParameter.getType(), schema)) {
			this.convertToComponentSchema(apiParameter, componentSchemas);
		}
		requestBody.setContent(content);
		return requestBody;
	}

	private void processPaths(OpenAPI openAPI, OASV3 OASV3, HashMap<String, Schema> componentSchemas) {
		HashMap<String, PathItem> paths = new HashMap<>();
		OASV3.setPaths(paths);
		RequestBody requestBody = null;
		List<Parameter> parameterList = new LinkedList<>();
		for(APIParameter apiParameter : openAPI.getInParameters()) {
			if(ParameterLocation.BODY.equals(apiParameter.getParameterLocation())) {
				requestBody = this.convertRequestBody(apiParameter, componentSchemas);
			} else {
				Parameter parameter = convertParameter(apiParameter, componentSchemas);
				parameterList.add(parameter);
			}
		}

		String methodTypeStr = openAPI.getRequestMapping().getMethodType().toLowerCase();
		String[] methodTypes = methodTypeStr.split(",");
		PathItem pathItem = new PathItem();
		pathItem.setDescription(openAPI.getDescription());
		pathItem.setParameters(parameterList);
		for(String methodType : methodTypes) {
			methodType = methodType.trim();
			Operation operation = new Operation();
			operation.setRequestBody(requestBody);
			operation.setTags(new LinkedList<>(openAPI.getTags()));
			operation.setOperationId(openAPI.getRequestMapping().getUrl() + methodType);
			HashMap<String, Response> responses = new HashMap<>();
			Response response = new Response();
			responses.put("200", response);
			response.setDescription(openAPI.getOutParameter() == null ? "null" :
					StringUtils.isEmpty(openAPI.getOutParameter().getDescription()) ?
							(StringUtils.isEmpty(openAPI.getOutParameter().getName()) ? openAPI.getOutParameter().getType() : openAPI.getOutParameter().getName()) :
							openAPI.getOutParameter().getDescription());
			HashMap<String, MediaType> content = new HashMap<>();
			MediaType mediaType = new MediaType();
			content.put("application/json", mediaType);
			if(openAPI.getOutParameter() != null) {
				Schema schema = new Schema();
				mediaType.setSchema(schema);
				if(setSchemaType(openAPI.getOutParameter().getType(), schema)) {
					this.convertToComponentSchema(openAPI.getOutParameter(), componentSchemas);
				}
			}
			response.setContent(content);
			operation.setResponses(responses);

			if(methodType.equalsIgnoreCase("get")) {
				pathItem.setGet(operation);
			} else if(methodType.equalsIgnoreCase("post")) {
				pathItem.setPost(operation);
			} else if(methodType.equalsIgnoreCase("options")) {
				pathItem.setOptions(operation);
			}
			paths.put((openAPI.getRequestMapping().getUrl().startsWith("/") ? "" : "/") +
					openAPI.getRequestMapping().getUrl(), pathItem);
		}
	}

	private Parameter convertParameter(APIParameter apiParameter, HashMap<String, Schema> componentSchemas) {
		Parameter parameter = new Parameter();
		parameter.setName(apiParameter.getName());
		parameter.setDescription(apiParameter.getDescription());
		parameter.setIn(apiParameter.getParameterLocation() != null ? apiParameter.getParameterLocation().toString().toLowerCase() : "");
		parameter.setRequired(ParameterOccurs.REQUIRED.equals(apiParameter.getParameterOccurs()));
		Schema schema = new Schema();
		parameter.setSchema(schema);
		if(setSchemaType(apiParameter.getType(), schema)) {
			this.convertToComponentSchema(apiParameter, componentSchemas);
		}
		return parameter;
	}

	private void convertToComponentSchema(APIParameter apiParameter, HashMap<String, Schema> componentSchemas) {
		Schema schema = new Schema();
		schema.setType("object");
		if(!CollectionUtils.isEmpty(apiParameter.getFields())) {
			HashMap<String, Schema> properties = new HashMap<>();
			for (APIParameter field: apiParameter.getFields()) {
				Schema fieldSchema = new Schema();
				if(setSchemaType(field.getType(), fieldSchema)) {
					this.convertToComponentSchema(field, componentSchemas);
				}
				properties.put(field.getName(), fieldSchema);
			}
			schema.setProperties(properties);
		}

		if(!apiParameter.getType().startsWith("java.util.List")) {
			String temp = org.apache.commons.lang.StringUtils.removeEnd(apiParameter.getType().trim(), "[]");
			temp = org.apache.commons.lang.StringUtils.substringBefore(temp, " ");
			if(!componentSchemas.containsKey(temp) || componentSchemas.get(temp).getProperties() == null) {
				componentSchemas.put(temp, schema);
			}
		}
	}

	private boolean setSchemaType(String paramType, Schema schema) {
		boolean result = false;
		if(paramType.equals("java.lang.String")) {
			schema.setType("string");
		} else if(paramType.equals("java.lang.Integer") || paramType.equals("int")) {
			schema.setType("integer");
			schema.setFormat("int32");
		} else if(paramType.equals("java.lang.Long") || paramType.equals("long")) {
			schema.setType("integer");
			schema.setFormat("int64");
		} else if(paramType.equals("java.lang.Boolean") || paramType.equals("boolean")) {
			schema.setType("boolean");
		} else if(paramType.equals("java.lang.Byte") || paramType.equals("byte")) {
			schema.setType("string");
			schema.setFormat("byte");
		} else if(paramType.equals("java.lang.Double") || paramType.equals("double")) {
			schema.setType("number");
			schema.setFormat("double");
		} else if(paramType.equals("java.lang.Float") || paramType.equals("float")) {
			schema.setType("number");
			schema.setFormat("float");
		} else if(paramType.equals("java.util.Date")) {
			schema.setType("string");
			schema.setFormat("date");
		} else if(paramType.startsWith("java.util.List")) {
			schema.setType("array");
			Schema items = new Schema();
			String listItemType = paramType.substring("java.util.List<".length(), paramType.length()-1);
			result = this.setSchemaType(listItemType, items);
			schema.setItems(items);
		} else if(paramType.endsWith("[]")) {
			schema.setType("array");
			Schema items = new Schema();
			String listItemType = paramType.substring(0, paramType.length() - 2);
			result = this.setSchemaType(listItemType, items);
			schema.setItems(items);
		} else if(paramType.startsWith("Enum[")) {
			schema.setType("string");
			String enumValueStr = paramType.substring("Enum[".length(), paramType.length()-1);
			List<String> enumValues = Arrays.asList(enumValueStr.split(","));
			schema.setEnumField(enumValues);
		} else {
			schema.setRef("#/components/schemas/" + org.apache.commons.lang.StringUtils.substringBefore(paramType, " "));
			result = true;
		}
		return result;
	}

	private void processOASInfo(OpenAPI openAPI, String version, OASV3 OASV3) {
		Info info = new Info();
		info.setTitle(StringUtils.isEmpty(openAPI.getBrief()) ? openAPI.getQualifiedName() : openAPI.getBrief());
		info.setDescription(openAPI.getDescription());
		info.setVersion(version);
		OASV3.setInfo(info);
	}

	/**
	 * Generate the class files for single classes specified on the command
	 * line.
	 * 
	 * @param classtree
	 *            the data structure representing the class tree.
	 */
	private void generateClassFiles(ClassTree classtree) {
		String[] packageNames = configuration.classDocCatalog.packageNames();
		for (int packageNameIndex = 0; packageNameIndex < packageNames.length; packageNameIndex++) {
			generateClassFiles(
					configuration.classDocCatalog
							.allClasses(packageNames[packageNameIndex]),
					classtree);
		}
	}
	
	protected String generateWRAPIFileName(RequestMapping requestMapping) {
		return StringUtils.strip(
				(requestMapping.getContainerName() + '-' + requestMapping.getUrl() 
					+ (requestMapping.getMethodType() == null ? '-'
						: '-' + requestMapping.getMethodType() + '-')
					+ (requestMapping.getHeaders() == null ? '-'
							: '-' + requestMapping.getHeaders() + '-')
					+ (requestMapping.getParams() == null ? '-'
							: '-' + requestMapping.getParams() + '-')
					+ (requestMapping.getConsumes() == null ? '-'
							: '-' + requestMapping.getConsumes() + '-')
				).replace('/', '-')
						.replace('\\', '-').replace(':', '-').replace('*', '-')
						.replace('?', '-').replace('"', '-').replace('<', '-')
						.replace('>', '-').replace('|', '-').replace('{', '-')
						.replace('}', '-'), "-")
				+ ".html";
		
	}
		
}
