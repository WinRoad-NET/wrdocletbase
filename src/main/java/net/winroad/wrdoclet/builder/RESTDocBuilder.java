package net.winroad.wrdoclet.builder;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.Configuration;
import com.sun.tools.doclets.internal.toolkit.util.Util;

import net.winroad.wrdoclet.AbstractConfiguration;
import net.winroad.wrdoclet.data.*;
import net.winroad.wrdoclet.taglets.WRAPITaglet;
import net.winroad.wrdoclet.taglets.WRRefRespTaglet;
import net.winroad.wrdoclet.taglets.WRTagTaglet;
import net.winroad.wrdoclet.utils.UniversalNamespaceCache;

import org.apache.commons.lang.StringUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import java.util.*;

/**
 * @author AdamsLee NOTE: WRDoc cannot cover API which returning objects whose
 *         type is unknown on API definition (known until runtime). 
 * 			e.g. @RequestMapping(value = "/update", method = RequestMethod.POST) 
 * 					public @ResponseBody
 *                       Object updateStudent(Student student) { return student;
 *                       }
 */
public class RESTDocBuilder extends AbstractDocBuilder {
	//TODO: dubbo支持多协议，如何处理一个服务类同时支持dubbo和http？
	public RESTDocBuilder(WRDoc wrDoc) {
		super(wrDoc);
		try {
			String dubboconfigpath = ((AbstractConfiguration) wrDoc
					.getConfiguration()).dubboconfigpath;
			if(!StringUtils.isBlank(dubboconfigpath)) {
				protocolMap = DubboDocBuilder.getDubboProtocols(dubboconfigpath);
			}
		} catch (Exception e) {
			this.logger.error(e);
		}
	}

	private PathMatcher matcher = new AntPathMatcher();
	private List<String> excludedUrls;
	/**
	 * dubbo配置的协议
	 * key protocol id, value "dubbo" or "http"
	 * http://alibaba.github.io/dubbo-doc-static/Configuration+Reference-showChildren=true.htm
	 */
	protected HashMap<String, String> protocolMap = new HashMap<>();

	protected List<String> getExcludedUrls(Configuration configuration) {
		List<String> excludedUrls = new LinkedList<String>();
		String contextConfigPath = ((AbstractConfiguration) configuration).springcontextconfigpath;
		if (!StringUtils.isBlank(contextConfigPath)) {
			String excludedUrlsXpath = ((AbstractConfiguration) configuration).excludedurlsxpath;
			try {
				Document contextConfig = readXMLConfig(contextConfigPath);
				XPath xPath = XPathFactory.newInstance().newXPath();
				xPath.setNamespaceContext(new UniversalNamespaceCache(
						contextConfig, false));
				if (StringUtils.isBlank(excludedUrlsXpath)) {
					NodeList serviceNodes = (NodeList) xPath
							.evaluate(
									"//:beans/mvc:interceptors/mvc:interceptor/mvc:exclude-mapping",
									contextConfig, XPathConstants.NODESET);
					for (int i = 0; i < serviceNodes.getLength(); i++) {
						Node node = serviceNodes.item(i);
						String path = getAttributeValue(node, "path");
						if (path != null) {
							excludedUrls.add(path);
						}
					}
				} else {
					NodeList serviceNodes = (NodeList) xPath.evaluate(
							excludedUrlsXpath, contextConfig,
							XPathConstants.NODESET);
					for (int i = 0; i < serviceNodes.getLength(); i++) {
						Node node = serviceNodes.item(i);
						excludedUrls.add(node.getTextContent());
					}
				}
			} catch (Exception e) {
				this.logger.error(e);
			}
		}
		this.logger.debug("excludedUrls: ");
		for (String s : excludedUrls) {
			this.logger.debug(s);
		}
		return excludedUrls;
	}

	@Override
	public void processOpenAPIClasses(ClassDoc[] classDocs,
			Configuration configuration) {
		this.excludedUrls = this.getExcludedUrls(configuration);
		processAnnotationDubboInterfaces(classDocs);
		for (int i = 0; i < classDocs.length; i++) {
			if (configuration.nodeprecated
					&& (Util.isDeprecated(classDocs[i]) || Util
							.isDeprecated(classDocs[i].containingPackage()))) {
				continue;
			}

			if (this.isController(classDocs[i])
					|| this.isAPIClass(classDocs[i])) {
				this.processControllerClass(classDocs[i], configuration);
				MethodDoc[] methods = classDocs[i].methods();
				for (int l = 0; l < methods.length; l++) {
					this.processOpenAPIMethod(methods[l], configuration);
				}
			}
		}
	}

	/*
	 * Parse the @RequestMapping from the MVC action method.
	 */
	@Override
	protected RequestMapping parseRequestMapping(MethodDoc methodDoc) {
		ClassDoc controllerClass = methodDoc.containingClass();
		RequestMapping baseMapping = null;
		boolean controllerCustomizedMapping = false;
		boolean actionCustomizedMapping = false;
		Tag[] tags = controllerClass.tags(WRAPITaglet.NAME);
		if (tags.length > 0) {
			baseMapping = this.parseRequestMapping(tags[0]);
		}

		if (baseMapping == null) {
			AnnotationDesc[] baseAnnotations = controllerClass.annotations();
			baseMapping = this.parseRequestMapping(baseAnnotations);
			controllerCustomizedMapping = false;
		} else {
			controllerCustomizedMapping = true;
		}

		RequestMapping mapping = null;
		Tag[] methodTags = methodDoc.tags(WRAPITaglet.NAME);
		if (methodTags.length > 0) {
			mapping = this.parseRequestMapping(methodTags[0]);
		}
		if (mapping == null) {
			AnnotationDesc[] annotations = methodDoc.annotations();
			mapping = this.parseRequestMapping(annotations);
			actionCustomizedMapping = false;
		} else {
			actionCustomizedMapping = true;
		}
		RequestMapping result;
		if (baseMapping == null) {
			result = mapping;
		} else if (mapping == null) {
			result = baseMapping;
		} else {
			if(!actionCustomizedMapping || controllerCustomizedMapping) {
				mapping.setUrl(net.winroad.wrdoclet.utils.Util.urlConcat(
						baseMapping.getUrl(), mapping.getUrl()));
			}
			if (baseMapping.getMethodType() != null) {
				if (mapping.getMethodType() != null) {
					mapping.setMethodType(baseMapping.getMethodType() + ","
							+ mapping.getMethodType());
				} else {
					mapping.setMethodType(baseMapping.getMethodType());
				}
			}
			result = mapping;
		}
		if (result != null) {
			result.setTooltip(methodDoc.containingClass().simpleTypeName());
			result.setContainerName(methodDoc.containingClass()
					.simpleTypeName());
		}
		return result;
	}

	/*
	 * Parse the RequestMapping from the annotations.
	 */
	private RequestMapping parseRequestMapping(AnnotationDesc[] annotations) {
		RequestMapping requestMapping = null;
		for (int i = 0; i < annotations.length; i++) {
			if (annotations[i].annotationType().name().equals("RequestMapping")) {
				requestMapping = new RequestMapping();
				setRequestMappingAttrs(requestMapping, annotations[i]);
				break;
			} else if (annotations[i].annotationType().name().equals("PostMapping")
					|| annotations[i].annotationType().name().equals("GetMapping")
					|| annotations[i].annotationType().name().equals("DeleteMapping")
					|| annotations[i].annotationType().name().equals("PutMapping")
					|| annotations[i].annotationType().name().equals("PatchMapping")) {
				requestMapping = new RequestMapping();
				requestMapping.setMethodType(annotations[i].annotationType().name().replace("Mapping","").toUpperCase());
				setRequestMappingAttrs(requestMapping, annotations[i]);
				break;
			}else if(annotations[i].annotationType().name().equals("Path")){
				if(requestMapping == null) {
					requestMapping = new RequestMapping() ;
				}
				for (int j = 0; j < annotations[i].elementValues().length; j++) {
					if ("value".equals(annotations[i].elementValues()[j]
							.element().name())) {
						String url = annotations[i].elementValues()[j].value()
								.toString().replace("\"", "");
						requestMapping.setUrl(url);
					}
				}
				if(requestMapping.getUrl() == null) {
					requestMapping.setUrl("");
				}
			} else if(annotations[i].annotationType().name().equals("GET")
					||annotations[i].annotationType().name().equals("POST")){
				if(requestMapping == null) {
					requestMapping = new RequestMapping() ;
				}
				requestMapping.setMethodType(annotations[i].annotationType().name());
			}
		}
		return requestMapping;
	}

	private void setRequestMappingAttrs(RequestMapping requestMapping, AnnotationDesc annotation) {
		for (int j = 0; j < annotation.elementValues().length; j++) {
			if ("value".equals(annotation.elementValues()[j]
					.element().name()) 
					|| "path".equals(annotation.elementValues()[j]
							.element().name())) {
				String url = annotation.elementValues()[j].value()
						.toString().replace("\"", "");
				requestMapping.setUrl(url);
			} else if ("method"
					.equals(annotation.elementValues()[j].element()
							.name())) {
				requestMapping
						.setMethodType(this
								.convertMethodType(annotation
										.elementValues()[j].value()
										.toString()));
			} else if ("params"
					.equals(annotation.elementValues()[j].element()
							.name())) {
				requestMapping
						.setParams(annotation
										.elementValues()[j].value()
										.toString().replace("\"", ""));
			} else if ("headers"
					.equals(annotation.elementValues()[j].element()
							.name())) {
				requestMapping
						.setHeaders(annotation
										.elementValues()[j].value()
										.toString().replace("\"", ""));
			} else if ("consumes"
					.equals(annotation.elementValues()[j].element()
							.name())) {
				requestMapping
						.setConsumes(annotation
										.elementValues()[j].value()
										.toString().replace("\"", ""));
			} else if ("produces"
					.equals(annotation.elementValues()[j].element()
							.name())) {
				requestMapping
						.setProduces(annotation
										.elementValues()[j].value()
										.toString().replace("\"", ""));
			}
		}
		if(requestMapping.getUrl() == null) {
			requestMapping.setUrl("");
		}
	}

	private RequestMapping parseRequestMapping(Tag tag) {
		RequestMapping requestMapping = null;
		if (!StringUtils.isEmpty(tag.text())) {
			requestMapping = new RequestMapping();
			int index = tag.text().indexOf(" ");
			if (index > 0) {
				String methodType = tag.text().substring(0, index);
				String url = tag.text().substring(index + 1);
				requestMapping.setMethodType(methodType);
				requestMapping.setUrl(url);
			} else {
				requestMapping.setUrl(tag.text());
			}
		}
		return requestMapping;
	}

	/*
	 * Simplify the methodType of @RequestMapping to display.
	 */
	private String convertMethodType(String methodType) {
		return methodType
				.replace(
						"org.springframework.web.bind.annotation.RequestMethod.",
						"").replace("{", "").replace("}", "");
	}
	
	@Override
	protected APIParameter getOutputParam(MethodDoc method) {
		APIParameter apiParameter = this.parseCustomizedReturn(method);
		if (apiParameter == null) {
			apiParameter = new APIParameter();
			apiParameter.setParameterOccurs(ParameterOccurs.REQUIRED);
			apiParameter.setType(this.getTypeName(method.returnType(), false));
			for (Tag tag : method.tags("return")) {
				apiParameter.setDescription(tag.text());
			}
			HashSet<String> processingClasses = new HashSet<String>();
			apiParameter.setFields(this.getFields(method.returnType(),
					ParameterType.Response, processingClasses));
			apiParameter.setHistory(this.getModificationHistory(method
					.returnType()));
		}

		apiParameter = handleRefResp(method, apiParameter);
		return apiParameter;
	}

	private APIParameter handleRefResp(MethodDoc method,
			APIParameter apiParameter) {
		if (apiParameter == null) {
			Tag[] tags = method.tags(WRRefRespTaglet.NAME);
			if (tags.length > 0) {
				apiParameter = new APIParameter();
				apiParameter.setType(tags[0].text());
				apiParameter.setParameterOccurs(ParameterOccurs.REQUIRED);
				HashSet<String> processingClasses = new HashSet<String>();
				ClassDoc c = this.wrDoc.getConfiguration().root
						.classNamed(apiParameter.getType());
				if (c != null) {
					apiParameter.setFields(this.getFields(c,
							ParameterType.Response, processingClasses));
				}
			}
		}
		return apiParameter;
	}
	
	/**
	 * param which will not be displayed in doc.
	 */
	protected boolean isInIgnoreParamList(Parameter param) {
		return "javax.servlet.http.HttpServletRequest".equals(param.type().qualifiedTypeName())
				|| "javax.servlet.http.HttpServletResponse".equals(param.type().qualifiedTypeName())
				|| "javax.servlet.http.HttpSession".equals(param.type().qualifiedTypeName())
				|| "org.springframework.web.context.request.WebRequest".equals(param.type().qualifiedTypeName())
				|| "java.io.OutputStream".equals(param.type().qualifiedTypeName())
				|| "org.springframework.http.HttpEntity<java.lang.String>".equals(param.type().qualifiedTypeName());
	}
	
	@Override
	protected List<APIParameter> getInputParams(MethodDoc method) {
		List<APIParameter> paramList = new LinkedList<APIParameter>();
		paramList.addAll(parseCustomizedParameters(method));
		Parameter[] parameters = method.parameters();
		for (int i = 0; i < parameters.length; i++) {
			if(this.isInIgnoreParamList(parameters[i])) {
				continue;
			}
			AnnotationDesc[] annotations = parameters[i].annotations();
			APIParameter apiParameter = new APIParameter();			
			apiParameter.setParameterOccurs(ParameterOccurs.REQUIRED);
			apiParameter.setType(this.getTypeName(parameters[i].type(), false));
			apiParameter.setName(parameters[i].name());

			HashSet<String> processingClasses = new HashSet<String>();
			apiParameter.setFields(this.getFields(parameters[i].type(),
					ParameterType.Request, processingClasses));
			apiParameter.setHistory(this
					.getModificationHistory(parameters[i].type()));
			StringBuffer buf = new StringBuffer();
			boolean paramComment = false;
			//TODO: RESTDocBuilder 和 DubboDocbuilder 需要看看怎么优化。因为dubbo也可以支持rest
			for (Tag tag : method.tags("param")) {
				if (parameters[i].name().equals(
						((ParamTag) tag).parameterName())) {
					paramComment = true;
					buf.append(((ParamTag) tag).parameterComment());
					buf.append(" ");
				}
			}
			if(!paramComment && this.methodMap.containsKey(method)) {
				for (Tag tag : this.methodMap.get(method).tags("param")) {
					if (parameters[i].name().equals(
							((ParamTag) tag).parameterName())) {
						buf.append(((ParamTag) tag).parameterComment());
						buf.append(" ");
					}
				}
			}
			for (int j = 0; j < annotations.length; j++) {
				processAnnotations(annotations[j], apiParameter);
				buf.append(annotations[j].toString().replace(
						annotations[j].annotationType().qualifiedTypeName(),
						annotations[j].annotationType().simpleTypeName()));
				buf.append(" ");
			}
			apiParameter.setDescription(buf.toString());
			apiParameter.setExample(this.getExample(method, parameters[i]));
			paramList.add(apiParameter);
		}

		handleRefReq(method, paramList);
		return paramList;
	}

	/*
	 * Here we only treat class which has spring "@Controller" annotation as a
	 * Controller, although it may not be enough.
	 */
	private boolean isController(ClassDoc classDoc) {
		return this.isProgramElementDocAnnotatedWith(classDoc, "org.springframework.stereotype.Controller") ||
				this.isProgramElementDocAnnotatedWith(classDoc, "org.springframework.web.bind.annotation.RestController") ||
				this.isRestService(classDoc);
	}

	protected boolean isRestService(ClassDoc classDoc) {
		AnnotationDesc[] annotations = classDoc.annotations();
		for (int i = 0; i < annotations.length; i++) {
			if (annotations[i].annotationType().qualifiedTypeName().equals("com.alibaba.dubbo.config.annotation.Service")) {
				AnnotationDesc.ElementValuePair[] elementValuePairs = annotations[i].elementValues();
				for (AnnotationDesc.ElementValuePair elementValuePair : elementValuePairs) {
					if("protocol".equals(elementValuePair.element().name())
							&& "http".equals(protocolMap.get(elementValuePair.value().toString().replace("\"", "")))) {
						return true;
					}
				}
				return false;
			}
		}
		return false;
	}

	private boolean isAPIClass(ClassDoc classDoc) {
		Tag[] t = classDoc.tags(WRAPITaglet.NAME);
		return t.length > 0;
	}

	protected void processAnnotationDubboInterfaces(ClassDoc[] classes) {
		for(ClassDoc classDoc:classes) {
			for(ClassDoc interfaceClassDoc : classDoc.interfaces()) {
				// mapping the method in implementation class to the method in interface
				for(MethodDoc implMethodDoc : classDoc.methods()) {
					MethodDoc overriddenMethod = implMethodDoc.overriddenMethod();
					if(overriddenMethod != null) {
						methodMap.put(implMethodDoc, overriddenMethod);
					} else {
						//It seems that MethodDoc.overriddenMethod() doesn't work, but MethodDoc.overrides() works fine.
						for(MethodDoc interfaceMethodDoc : interfaceClassDoc.methods()) {
							if(implMethodDoc.overrides(interfaceMethodDoc)) {
								methodMap.put(implMethodDoc, interfaceMethodDoc);
							}
						}
					}
				}
			}
		}
	}

	/*
	 * Process the tag on the Controller.
	 */
	private void processControllerClass(ClassDoc controller,
			Configuration configuration) {
		Tag[] controllerTagArray = controller.tags(WRTagTaglet.NAME);
		for (int i = 0; i < controllerTagArray.length; i++) {
			Set<String> controllerTags = WRTagTaglet
					.getTagSet(controllerTagArray[i].text());
			for (Iterator<String> iter = controllerTags.iterator(); iter
					.hasNext();) {
				String tag = iter.next();
				if (!this.taggedOpenAPIMethods.containsKey(tag)) {
					this.taggedOpenAPIMethods
							.put(tag, new HashSet<MethodDoc>());
				}
				// all action method of this controller should be processed
				// later.
				for (int j = 0; j < controller.methods().length; j++) {
					if (configuration.nodeprecated
							&& Util.isDeprecated(controller.methods()[j])) {
						continue;
					}
					if (isOpenAPIMethod(controller.methods()[j])) {
						this.taggedOpenAPIMethods.get(tag).add(
								controller.methods()[j]);
					}
				}
			}

			this.wrDoc.getWRTags().addAll(controllerTags);
		}
	}

	/*
	 * The method has spring "@RequestMapping" or tagged as "api" or resteasy "@Path".
	 */
	protected boolean isOpenAPIMethod(MethodDoc methodDoc) {
		AnnotationDesc[] annotations = methodDoc.annotations();
		boolean isActionMethod = false;
		for (int i = 0; i < annotations.length; i++) {
			if (annotations[i].annotationType().name().equals("RequestMapping")
					|| annotations[i].annotationType().name().equals("PostMapping")
					|| annotations[i].annotationType().name().equals("GetMapping")
					|| annotations[i].annotationType().name().equals("DeleteMapping")
					|| annotations[i].annotationType().name().equals("PutMapping")
					|| annotations[i].annotationType().name().equals("PatchMapping") ) {
				isActionMethod = true;
				break;
			}
		}
		Tag[] t = methodDoc.tags(WRAPITaglet.NAME);
		return isActionMethod
				|| t.length > 0
				|| this.isProgramElementDocAnnotatedWith(methodDoc, "javax.ws.rs.Path");
	}

	/*
	 * Whether the method is annotated with @ResponseBody.
	 */
	private boolean isAnnotatedResponseBody(MethodDoc method) {
		AnnotationDesc[] annotations = method.annotations();
		for (int i = 0; i < annotations.length; i++) {
			if (annotations[i].annotationType().name().equals("ResponseBody")) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected int isAPIAuthNeeded(String url) {
		if (url != null && this.excludedUrls != null
				&& this.excludedUrls.size() != 0) {
			if (url.startsWith("{") && url.endsWith("}")) {
				url = StringUtils.substring(url, 1, url.length() - 1);
			}
			String[] urls = url.split(",");
			for (String u : urls) {
				for (String excludedUrl : this.excludedUrls) {
					if (matcher.match(excludedUrl, u)) {
						return 0;
					}
				}
				return 1;
			}
		}
		return -1;
	}

	@Override
	protected RequestMapping parseRequestMapping(ClassDoc classDoc) {
		// not needed
		return null;
	}

	@Override
	protected APIParameter getOutputParam(ClassDoc classDoc) {
		// not needed
		return null;
	}

	@Override
	protected APIParameter getInputParams(ClassDoc classDoc) {
		// not needed
		return null;
	}

}
