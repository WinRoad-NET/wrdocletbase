package net.winroad.wrdoclet.builder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import net.winroad.wrdoclet.AbstractConfiguration;
import net.winroad.wrdoclet.data.APIParameter;
import net.winroad.wrdoclet.data.ParameterOccurs;
import net.winroad.wrdoclet.data.ParameterType;
import net.winroad.wrdoclet.data.RequestMapping;
import net.winroad.wrdoclet.data.WRDoc;
import net.winroad.wrdoclet.utils.LoggerFactory;
import net.winroad.wrdoclet.utils.UniversalNamespaceCache;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.ParamTag;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.Tag;
import com.sun.tools.doclets.internal.toolkit.Configuration;

public class DubboDocBuilder extends AbstractServiceDocBuilder {
	protected LinkedList<String> dubboInterfaces = null;

	//methodDoc in interfaces map to methodDoc in implementation classes
	protected HashMap<MethodDoc, MethodDoc> stubImplMethodMap = new HashMap<>();
	
	public DubboDocBuilder(WRDoc wrDoc) {
		super(wrDoc);
		this.logger = LoggerFactory.getLogger(this.getClass());
		dubboInterfaces = this.getDubboInterfaces();
	}
	
	@Override
	protected void processOpenAPIClasses(ClassDoc[] classes,
			Configuration configuration) {
		LinkedList<String> annotationDubboInterfaces = processAnnotationDubboInterfaces(classes);
		dubboInterfaces.addAll(annotationDubboInterfaces);
		super.processOpenAPIClasses(classes, configuration);
	}

	protected LinkedList<String> processAnnotationDubboInterfaces(ClassDoc[] classes) {
		LinkedList<String> result = new LinkedList<String>();
		for (int i = 0; i < classes.length; i++) {
			// implementation class which used com.alibaba.dubbo.config.annotation.Service 
			if(isClassDocAnnotatedWith(classes[i],"Service")) {
				for(ClassDoc interfaceClassDoc : classes[i].interfaces()) {
					result.add(interfaceClassDoc.qualifiedName());
					// mapping the method in interface to the method in implementation class
					for(MethodDoc implMethodDoc : classes[i].methods()) {
						MethodDoc overriddenMethod = implMethodDoc.overriddenMethod();
						if(overriddenMethod != null) {
							stubImplMethodMap.put(overriddenMethod, implMethodDoc);
						} else {
							//It seems that MethodDoc.overriddenMethod() doesn't work, but MethodDoc.overrides() works fine.
							for(MethodDoc interfaceMethodDoc : interfaceClassDoc.methods()) {
								if(implMethodDoc.overrides(interfaceMethodDoc)) {
									stubImplMethodMap.put(interfaceMethodDoc, implMethodDoc);
								}
							}
						}
					}
				}
			}
		}
		return result;
	}
	
	protected LinkedList<String> getDubboInterfaces() {
		LinkedList<String> result = new LinkedList<String>();
		try {
			Document dubboConfig = readXMLConfig(((AbstractConfiguration) this.wrDoc
					.getConfiguration()).dubboconfigpath);
			XPath xPath = XPathFactory.newInstance().newXPath();
			xPath.setNamespaceContext(new UniversalNamespaceCache(dubboConfig,
					false));
			NodeList serviceNodes = (NodeList) xPath.evaluate(
					"//:beans/dubbo:service", dubboConfig,
					XPathConstants.NODESET);
			for (int i = 0; i < serviceNodes.getLength(); i++) {
				Node node = serviceNodes.item(i);
				String ifc = getAttributeValue(node, "interface");
				if (ifc != null)
					result.add(ifc);
			}
		} catch (Exception e) {
			this.logger.error(e);
		}
		this.logger.debug("dubbo interface list:");
		for (String s : result) {
			this.logger.debug("interface: " + s);
		}
		return result;
	}

	@Override
	protected RequestMapping parseRequestMapping(MethodDoc methodDoc) {
		RequestMapping mapping = new RequestMapping();
		mapping.setUrl(methodDoc.toString().replaceFirst(
				methodDoc.containingClass().qualifiedName() + ".", ""));
		mapping.setTooltip(methodDoc.containingClass().simpleTypeName());
		mapping.setContainerName(methodDoc.containingClass().simpleTypeName());
		return mapping;
	}

	@Override
	protected APIParameter getOutputParam(MethodDoc methodDoc) {
		APIParameter apiParameter = null;
		if (methodDoc.returnType() != null) {
			apiParameter = new APIParameter();
			apiParameter.setParameterOccurs(ParameterOccurs.REQUIRED);
			apiParameter.setType(this.getTypeName(methodDoc.returnType(), false));
			for (Tag tag : methodDoc.tags("return")) {
				apiParameter.setDescription(tag.text());
			}
			HashSet<String> processingClasses = new HashSet<String>();
			apiParameter.setFields(this.getFields(methodDoc.returnType(),
					ParameterType.Response, processingClasses));
			apiParameter.setHistory(this.getModificationHistory(methodDoc
					.returnType()));
		}
		return apiParameter;
	}

	@Override
	protected List<APIParameter> getInputParams(MethodDoc method) {
		List<APIParameter> paramList = new LinkedList<APIParameter>();
		paramList.addAll(parseCustomizedParameters(method));
		Parameter[] parameters = method.parameters();
		MethodDoc implMethod = stubImplMethodMap.get(method);
		Parameter[] implParams = implMethod == null ? null : implMethod.parameters();
		for (int i = 0; i < parameters.length; i++) {
			AnnotationDesc[] annotations = parameters[i].annotations();
			AnnotationDesc[] implAnnotations = implParams == null ? null : implParams[i].annotations();
			APIParameter apiParameter = new APIParameter();
			if(annotations.length == 0 && implAnnotations != null) {
				annotations = implAnnotations;
			}
			apiParameter.setType(this.getTypeName(parameters[i].type(), false));
			apiParameter.setName(parameters[i].name());
			HashSet<String> processingClasses = new HashSet<String>();
			apiParameter.setFields(this.getFields(parameters[i].type(),
					ParameterType.Request, processingClasses));
			apiParameter.setHistory(this
					.getModificationHistory(parameters[i].type()));
			StringBuffer buf = new StringBuffer();
			for (Tag tag : method.tags("param")) {
				if (parameters[i].name().equals(
						((ParamTag) tag).parameterName())) {
					buf.append(((ParamTag) tag).parameterComment());
					buf.append(" ");
				}
			}
			for (int j = 0; j < annotations.length; j++) {
				processAnnotations(annotations[j], apiParameter);
				buf.append("@");
				buf.append(annotations[j].annotationType().name());
				buf.append(" ");
			}
			apiParameter.setDescription(buf.toString());
			paramList.add(apiParameter);
		}

		handleRefReq(method, paramList);
		return paramList;
	}


	@Override
	protected boolean isServiceInterface(ClassDoc classDoc) {
		return classDoc.isInterface()
				&& dubboInterfaces.contains(classDoc.qualifiedName());
	}

	@Override
	protected int isAPIAuthNeeded(String url) {
		//no authentication
		return -1;
	}

}
