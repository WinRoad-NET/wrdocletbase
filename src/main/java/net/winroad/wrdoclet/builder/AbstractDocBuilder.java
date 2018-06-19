package net.winroad.wrdoclet.builder;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.winroad.wrdoclet.AbstractConfiguration;
import net.winroad.wrdoclet.data.*;
import net.winroad.wrdoclet.taglets.WRBriefTaglet;
import net.winroad.wrdoclet.taglets.WREgdescriptionTaglet;
import net.winroad.wrdoclet.taglets.WREgexternalvalueTaglet;
import net.winroad.wrdoclet.taglets.WREgnameTaglet;
import net.winroad.wrdoclet.taglets.WREgsummaryTaglet;
import net.winroad.wrdoclet.taglets.WREgvalueTaglet;
import net.winroad.wrdoclet.taglets.WRMemoTaglet;
import net.winroad.wrdoclet.taglets.WRMqConsumerTaglet;
import net.winroad.wrdoclet.taglets.WRMqProducerTaglet;
import net.winroad.wrdoclet.taglets.WROccursTaglet;
import net.winroad.wrdoclet.taglets.WRParamTaglet;
import net.winroad.wrdoclet.taglets.WRRefReqTaglet;
import net.winroad.wrdoclet.taglets.WRReturnCodeTaglet;
import net.winroad.wrdoclet.taglets.WRReturnTaglet;
import net.winroad.wrdoclet.taglets.WRTagTaglet;
import net.winroad.wrdoclet.utils.ApplicationContextConfig;
import net.winroad.wrdoclet.utils.Logger;
import net.winroad.wrdoclet.utils.LoggerFactory;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MemberDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.ParamTag;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.ParameterizedType;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.Tag;
import com.sun.javadoc.Type;
import com.sun.javadoc.AnnotationDesc.ElementValuePair;
import com.sun.tools.doclets.internal.toolkit.Configuration;
import com.sun.tools.doclets.internal.toolkit.util.Util;

public abstract class AbstractDocBuilder {
	protected Logger logger;

	protected WRDoc wrDoc;

	protected Map<String, Set<MethodDoc>> taggedOpenAPIMethods = new HashMap<String, Set<MethodDoc>>();

	protected Map<String, Set<ClassDoc>> taggedOpenAPIClasses = new HashMap<String, Set<ClassDoc>>();

	protected Set<String> annotationSetToShow = new HashSet<>();

	public AbstractDocBuilder(WRDoc wrDoc) {
		this.wrDoc = wrDoc;
		this.logger = LoggerFactory.getLogger(this.getClass());
	}

	public WRDoc getWrDoc() {
		return wrDoc;
	}

	public void setWrDoc(WRDoc wrDoc) {
		this.wrDoc = wrDoc;
	}

	public Map<String, Set<MethodDoc>> getTaggedOpenAPIMethods() {
		return taggedOpenAPIMethods;
	}

	/**
	 * methodDoc in implementation classes map to methodDoc in interfaces or
	 * methodDoc in interfaces map to methodDoc in implementation classes
	 */
	protected HashMap<MethodDoc, MethodDoc> methodMap = new HashMap<>();

	public void setTaggedOpenAPIMethods(Map<String, Set<MethodDoc>> taggedOpenAPIMethods) {
		this.taggedOpenAPIMethods = taggedOpenAPIMethods;
	}

	public void buildWRDoc() {
		this.processShowAnnotationList(this.wrDoc.getConfiguration());
		this.processOpenAPIClasses(this.wrDoc.getConfiguration().root.classes(), this.wrDoc.getConfiguration());
		this.buildOpenAPIs(this.wrDoc.getConfiguration());
		this.buildOpenAPIByClasses(this.wrDoc.getConfiguration());
	}

	protected void processShowAnnotationList(Configuration configuration) {
		String showAnnotationList = ((AbstractConfiguration) configuration).showAnnotationList;
		if(!org.springframework.util.StringUtils.isEmpty(showAnnotationList)) {
			for(String annotation : showAnnotationList.split(",")) {
				annotationSetToShow.add(annotation);
			}
		}
	}

	protected abstract void processOpenAPIClasses(ClassDoc[] classDocs, Configuration configuration);

	protected Tag[] getTagTaglets(MethodDoc methodDoc) {
		return (Tag[]) ArrayUtils.addAll(methodDoc.tags(WRTagTaglet.NAME),
				methodDoc.containingClass().tags(WRTagTaglet.NAME));
	}

	protected void processOpenAPIMethod(MethodDoc methodDoc, Configuration configuration) {
		if ((configuration.nodeprecated && Util.isDeprecated(methodDoc)) || !isOpenAPIMethod(methodDoc)) {
			return;
		}

		Tag[] methodTagArray = getTagTaglets(methodDoc);
		if (methodTagArray.length == 0) {
			String tag = methodDoc.containingClass().simpleTypeName();
			this.wrDoc.getWRTags().add(tag);
			if (!this.taggedOpenAPIMethods.containsKey(tag)) {
				this.taggedOpenAPIMethods.put(tag, new HashSet<MethodDoc>());
			}
			this.taggedOpenAPIMethods.get(tag).add(methodDoc);
		} else {
			for (int i = 0; i < methodTagArray.length; i++) {
				Set<String> methodTags = WRTagTaglet.getTagSet(methodTagArray[i].text());
				this.wrDoc.getWRTags().addAll(methodTags);
				for (Iterator<String> iter = methodTags.iterator(); iter.hasNext();) {
					String tag = iter.next();
					if (!this.taggedOpenAPIMethods.containsKey(tag)) {
						this.taggedOpenAPIMethods.put(tag, new HashSet<MethodDoc>());
					}
					this.taggedOpenAPIMethods.get(tag).add(methodDoc);
				}
			}
		}
	}

	protected String getBriefFromCommentText(String commentText) {
		int index = StringUtils.indexOf(commentText, '\n');
		if (index != -1) {
			commentText = StringUtils.substring(commentText, 0, index);
		}
		index = StringUtils.indexOfAny(commentText, ".!?。！？…");
		if (index > 0) {
			commentText = StringUtils.substring(commentText, 0, index);
		}
		if (StringUtils.length(commentText) > 8) {
			commentText = StringUtils.substring(commentText, 0, 8) + "…";
		}
		return commentText;
	}

	protected void buildOpenAPIByClasses(Configuration configuration) {
		Set<Entry<String, Set<ClassDoc>>> classes = this.taggedOpenAPIClasses.entrySet();
		for (Iterator<Entry<String, Set<ClassDoc>>> tagClsIter = classes.iterator(); tagClsIter.hasNext();) {
			Entry<String, Set<ClassDoc>> kv = tagClsIter.next();
			String tagName = kv.getKey();
			if (!this.wrDoc.getTaggedOpenAPIs().containsKey(tagName)) {
				this.wrDoc.getTaggedOpenAPIs().put(tagName, new LinkedList<OpenAPI>());
			}
			Set<ClassDoc> classDocSet = kv.getValue();
			for (Iterator<ClassDoc> clsIter = classDocSet.iterator(); clsIter.hasNext();) {
				ClassDoc classDoc = clsIter.next();
				OpenAPI openAPI = new OpenAPI();
				openAPI.setDeprecated(Util.isDeprecated(classDoc) || Util.isDeprecated(classDoc.containingPackage()));
				Tag[] tags = classDoc.tags(WRTagTaglet.NAME);
				if (tags.length == 0) {
					openAPI.addTag(tagName);
				} else {
					for (Tag t : tags) {
						openAPI.addTags(WRTagTaglet.getTagSet(t.text()));
					}
				}
				openAPI.setQualifiedName(classDoc.qualifiedName());
				if (StringUtils.isNotBlank(classDoc.commentText())) {
					openAPI.setDescription(classDoc.commentText());
				}

				String brief;
				if (classDoc.tags(WRBriefTaglet.NAME).length == 0) {
					brief = getBriefFromCommentText(classDoc.commentText());
				} else {
					brief = classDoc.tags(WRBriefTaglet.NAME)[0].text();
				}

				openAPI.setBrief(brief);
				if (StringUtils.isBlank(openAPI.getDescription())) {
					openAPI.setDescription(openAPI.getBrief());
				}

				openAPI.setModificationHistory(this.getModificationHistory(classDoc));
				openAPI.setRequestMapping(this.parseRequestMapping(classDoc));
				openAPI.addInParameter(this.getInputParams(classDoc));
				openAPI.setOutParameter(this.getOutputParam(classDoc));
				this.wrDoc.getTaggedOpenAPIs().get(tagName).add(openAPI);
			}
		}

	}

	protected void buildOpenAPIs(Configuration configuration) {
		Set<Entry<String, Set<MethodDoc>>> methods = this.taggedOpenAPIMethods.entrySet();
		for (Iterator<Entry<String, Set<MethodDoc>>> tagMthIter = methods.iterator(); tagMthIter.hasNext();) {
			Entry<String, Set<MethodDoc>> kv = tagMthIter.next();
			String tagName = kv.getKey();
			if (!this.wrDoc.getTaggedOpenAPIs().containsKey(tagName)) {
				this.wrDoc.getTaggedOpenAPIs().put(tagName, new LinkedList<OpenAPI>());
			}
			Set<MethodDoc> methodDocSet = kv.getValue();
			for (Iterator<MethodDoc> mthIter = methodDocSet.iterator(); mthIter.hasNext();) {
				MethodDoc methodDoc = mthIter.next();
				OpenAPI openAPI = new OpenAPI();
				openAPI.setDeprecated(Util.isDeprecated(methodDoc) || Util.isDeprecated(methodDoc.containingClass())
						|| Util.isDeprecated(methodDoc.containingPackage()));
				Tag[] tags = this.getTagTaglets(methodDoc);
				if (tags.length == 0 && this.methodMap.containsKey(methodDoc)) {
					tags = this.getTagTaglets(this.methodMap.get(methodDoc));
				}
				if (tags.length == 0) {
					openAPI.addTag(methodDoc.containingClass().simpleTypeName());
				} else {
					for (Tag t : tags) {
						openAPI.addTags(WRTagTaglet.getTagSet(t.text()));
					}
				}
				openAPI.setQualifiedName(methodDoc.qualifiedName());
				if (StringUtils.isNotBlank(methodDoc.commentText())) {
					openAPI.setDescription(methodDoc.commentText());
				} else if (this.methodMap.containsKey(methodDoc)) {
					openAPI.setDescription(this.methodMap.get(methodDoc).commentText());
				}

				String brief;
				if (methodDoc.tags(WRBriefTaglet.NAME).length == 0) {
					brief = getBriefFromCommentText(methodDoc.commentText());
				} else {
					brief = methodDoc.tags(WRBriefTaglet.NAME)[0].text();
				}
				if (StringUtils.isBlank(brief) && this.methodMap.containsKey(methodDoc)) {
					if (this.methodMap.get(methodDoc).tags(WRBriefTaglet.NAME).length == 0) {
						brief = getBriefFromCommentText(this.methodMap.get(methodDoc).commentText());
					} else {
						brief = this.methodMap.get(methodDoc).tags(WRBriefTaglet.NAME)[0].text();
					}
				}
				openAPI.setBrief(brief);
				if (StringUtils.isBlank(openAPI.getDescription())) {
					openAPI.setDescription(openAPI.getBrief());
				}

				openAPI.setModificationHistory(this.getModificationHistory(methodDoc));
				openAPI.setRequestMapping(this.parseRequestMapping(methodDoc));
				openAPI.setAuthNeeded(this.isAuthNeededByAnnotation(methodDoc));
				if (openAPI.getRequestMapping() != null && openAPI.getAuthNeeded() == -1) {
					openAPI.setAuthNeeded(this.isAPIAuthNeeded(openAPI.getRequestMapping().getUrl()));
				}
				openAPI.addInParameters(this.getInputParams(methodDoc));
				openAPI.setOutParameter(this.getOutputParam(methodDoc));
				openAPI.setReturnCode(this.getReturnCode(methodDoc));
				openAPI.setRemark(this.getRemark(methodDoc));
				this.wrDoc.getTaggedOpenAPIs().get(tagName).add(openAPI);
			}
		}
	}

	protected int isAuthNeededByAnnotation(MethodDoc methodDoc) {
		String authKeyword = ((AbstractConfiguration)this.wrDoc.getConfiguration()).authKeyword;
		String noAuthKeyword = ((AbstractConfiguration)this.wrDoc.getConfiguration()).noAuthKeyword;
		if(!StringUtils.isBlank(authKeyword) || !StringUtils.isBlank(noAuthKeyword)) {
			String[] authKeywords = authKeyword.split(",");
			String[] noAuthKeywords = noAuthKeyword.split(",");
			AnnotationDesc[] annotations = methodDoc.annotations();
			for (int i = 0; i < annotations.length; i++) {
				for(String keyword : authKeywords) {
					if (annotations[i].toString().contains(keyword)) {
						return 1;
					}
				}
				for(String keyword : noAuthKeywords) {
					if (annotations[i].toString().contains(keyword)) {
						return 0;
					}
				}
			}
		}
		return -1;
	}

	protected String getRemark(MethodDoc methodDoc) {
		StringBuilder stringBuilder = new StringBuilder();
		AnnotationDesc[] annotations = methodDoc.annotations();
		for (int i = 0; i < annotations.length; i++) {
			if (this.annotationSetToShow.contains(annotations[i].annotationType().qualifiedTypeName())) {
				stringBuilder.append(this.getSimpleAnnotationStr(annotations[i]));
				stringBuilder.append(",");
			}
		}
		return org.springframework.util.StringUtils.trimTrailingCharacter(stringBuilder.toString(),',');
	}

	/**
	 * @param url
	 *            url of API.
	 * @return 0 for anonymous allowed, 1 for authentication needed, others for not
	 *         specified.
	 */
	protected abstract int isAPIAuthNeeded(String url);

	protected abstract boolean isOpenAPIMethod(MethodDoc methodDoc);

	protected abstract RequestMapping parseRequestMapping(MethodDoc methodDoc);

	protected abstract RequestMapping parseRequestMapping(ClassDoc classDoc);

	protected abstract APIParameter getOutputParam(MethodDoc methodDoc);

	protected abstract APIParameter getOutputParam(ClassDoc classDoc);

	protected abstract List<APIParameter> getInputParams(MethodDoc methodDoc);

	protected abstract APIParameter getInputParams(ClassDoc classDoc);

	protected String getParamComment(MethodDoc method, String paramName) {
		ParamTag[] paramTags = method.paramTags();
		for (ParamTag paramTag : paramTags) {
			if (paramTag.parameterName().equals(paramName)) {
				return paramTag.parameterComment();
			}
		}
		return null;
	}

	protected boolean isProgramElementDocAnnotatedWith(ProgramElementDoc elementDoc, String annotation) {
		AnnotationDesc[] annotations = elementDoc.annotations();
		for (int i = 0; i < annotations.length; i++) {
			if (annotations[i].annotationType().qualifiedTypeName().equals(annotation)) {
				return true;
			}
		}
		return false;
	}

	protected ModificationHistory getModificationHistory(ClassDoc classDoc) {
		ModificationHistory history = new ModificationHistory();
		if (classDoc != null) {
			LinkedList<ModificationRecord> list = this.getModificationRecords(classDoc);
			history.addModificationRecords(list);
		}
		return history;
	}

	/*
	 * get the modification history of the class.
	 */
	protected ModificationHistory getModificationHistory(Type type) {
		ModificationHistory history = new ModificationHistory();
		ClassDoc classDoc = this.wrDoc.getConfiguration().root.classNamed(type.qualifiedTypeName());
		if (classDoc != null) {
			LinkedList<ModificationRecord> list = this.getModificationRecords(classDoc);
			history.addModificationRecords(list);
		}
		return history;
	}

	/*
	 * get the modification history of the method.
	 */
	protected ModificationHistory getModificationHistory(MethodDoc methodDoc) {
		ModificationHistory history = new ModificationHistory();
		history.addModificationRecords(this.parseModificationRecords(methodDoc.tags()));
		return history;
	}

	/*
	 * get the modification records of the class.
	 */
	protected LinkedList<ModificationRecord> getModificationRecords(ClassDoc classDoc) {
		ClassDoc superClass = classDoc.superclass();
		if (superClass == null) {
			return new LinkedList<ModificationRecord>();
		}
		LinkedList<ModificationRecord> result = this.getModificationRecords(superClass);
		result.addAll(this.parseModificationRecords(classDoc.tags()));
		return result;
	}

	/*
	 * Parse tags to get customized parameters.
	 */
	protected LinkedList<APIParameter> parseCustomizedParameters(MethodDoc methodDoc) {
		Tag[] tags = methodDoc.tags(WRParamTaglet.NAME);
		LinkedList<APIParameter> result = new LinkedList<APIParameter>();
		for (int i = 0; i < tags.length; i++) {
			result.add(WRParamTaglet.parse(tags[i].text()));
		}
		return result;
	}

	/*
	 * Parse tags to get customized return.
	 */
	protected APIParameter parseCustomizedReturn(MethodDoc methodDoc) {
		Tag[] tags = methodDoc.tags(WRReturnTaglet.NAME);
		APIParameter result = null;
		if (tags.length > 0) {
			result = WRReturnTaglet.parse(tags[0].text());
		}
		return result;
	}

	/*
	 * Parse tags to get modification records.
	 */
	protected LinkedList<ModificationRecord> parseModificationRecords(Tag[] tags) {
		LinkedList<ModificationRecord> result = new LinkedList<ModificationRecord>();
		for (int i = 0; i < tags.length; i++) {
			if ("@author".equalsIgnoreCase(tags[i].name())) {
				ModificationRecord record = new ModificationRecord();
				record.setModifier(tags[i].text());

				if (i + 1 < tags.length) {
					if ("@version".equalsIgnoreCase(tags[i + 1].name())) {
						record.setVersion(tags[i + 1].text());
						if (i + 2 < tags.length && ("@" + WRMemoTaglet.NAME).equalsIgnoreCase(tags[i + 2].name())) {
							record.setMemo(tags[i + 2].text());
						}
					} else if (("@" + WRMemoTaglet.NAME).equalsIgnoreCase(tags[i + 1].name())) {
						record.setMemo(tags[i + 1].text());
					}
				}
				result.add(record);
			}
		}

		return result;
	}

	protected String getReturnCode(MethodDoc methodDoc) {
		Tag[] tags = methodDoc.tags(WRReturnCodeTaglet.NAME);
		return WRReturnCodeTaglet.concat(tags);
	}

	protected String getMQConsumerTopic(ClassDoc classDoc) {
		Tag[] tags = classDoc.tags(WRMqConsumerTaglet.NAME);
		if (tags.length == 0) {
			return "";
		}
		return StringUtils.substringBefore(tags[0].text(), "\n");
	}

	protected String getMQProducerTopic(ClassDoc classDoc) {
		Tag[] tags = classDoc.tags(WRMqProducerTaglet.NAME);
		if (tags.length == 0) {
			return "";
		}
		return StringUtils.substringBefore(tags[0].text(), "\n");
	}

	protected boolean isInStopClasses(ClassDoc classDoc) {
		String property = ApplicationContextConfig.getStopClasses();
		if (property != null) {
			String[] stopClasses = property.split(",");
			String[] cdParts = classDoc.qualifiedTypeName().split("\\.");
			for (String stopClass : stopClasses) {
				String[] scParts = stopClass.trim().split("\\.");
				if (scParts.length <= cdParts.length) {
					boolean hasDiffPart = false;
					for (int i = 0; i < scParts.length; i++) {
						if (scParts[i].equals("*")) {
							return true;
						} else if (!scParts[i].equalsIgnoreCase(cdParts[i])) {
							hasDiffPart = true;
							break;
						}
					}
					if (scParts.length == cdParts.length && !hasDiffPart) {
						return true;
					}
				}
			}
		}

		return false;
	}

	protected boolean isParameterizedTypeInStopClasses(Type type) {
		if (!this.isInStopClasses(type.asClassDoc())) {
			return false;
		}
		ParameterizedType pt = type.asParameterizedType();
		if (pt != null) {
			for (Type arg : pt.typeArguments()) {
				if (!this.isParameterizedTypeInStopClasses(arg)) {
					return false;
				}
			}
		}
		return true;
	}

	protected List<APIParameter> getFields(Type type, ParameterType paramType, HashSet<String> processingClasses) {
		processingClasses.add(type.toString());
		List<APIParameter> result = new LinkedList<APIParameter>();
		if (!type.isPrimitive()) {
			ParameterizedType pt = type.asParameterizedType();
			if (pt != null && pt.typeArguments().length > 0) {
				for (Type arg : pt.typeArguments()) {
					if (!this.isParameterizedTypeInStopClasses(arg)) {
						APIParameter tmp = new APIParameter();
						tmp.setName(arg.simpleTypeName());
						tmp.setType(this.getTypeName(arg, false));
						tmp.setDescription("");
						tmp.setParentTypeArgument(true);
						if (!processingClasses.contains(arg.qualifiedTypeName())) {
							tmp.setFields(this.getFields(arg, paramType, processingClasses));
						}
						result.add(tmp);
					}
				}
			}

			ClassDoc classDoc = this.wrDoc.getConfiguration().root.classNamed(type.qualifiedTypeName());
			if (classDoc != null) {
				result.addAll(this.getFields(classDoc, paramType, processingClasses));
			}
		}
		return result;
	}

	protected List<APIParameter> getFields(ClassDoc classDoc, ParameterType paramType,
			HashSet<String> processingClasses) {
		processingClasses.add(classDoc.toString());
		List<APIParameter> result = new LinkedList<APIParameter>();

		boolean isLomBokClass = this.isProgramElementDocAnnotatedWith(classDoc, "lombok.Data")
				|| (paramType == ParameterType.Response
						&& this.isProgramElementDocAnnotatedWith(classDoc, "lombok.Getter"))
				|| (paramType == ParameterType.Request
						&& this.isProgramElementDocAnnotatedWith(classDoc, "lombok.Setter"));

		// todo
		// this.wrDoc.getConfiguration().root.classNamed(type.qualifiedTypeName()).typeParameters()[0].qualifiedTypeName()

		ClassDoc superClassDoc = classDoc.superclass();
		if (superClassDoc != null && !this.isInStopClasses(superClassDoc)
				&& !processingClasses.contains(superClassDoc.qualifiedTypeName())) {
			result.addAll(this.getFields(superClassDoc, paramType, processingClasses));
		}

		if (this.isInStopClasses(classDoc)) {
			return result;
		}

		FieldDoc[] fieldDocs = classDoc.fields(false);
		HashMap<String, String> privateFieldValidator = new HashMap<>();
		HashMap<String, String> privateFieldDesc = new HashMap<>();
		HashMap<String, Example> privateFieldExample = new HashMap<>();
		HashMap<String, String> privateJsonField = new HashMap<>();
		Set<String> transientFieldSet = new HashSet<>();

		for (FieldDoc fieldDoc : fieldDocs) {
			if (!fieldDoc.isTransient() && !fieldDoc.isStatic()
					&& (fieldDoc.isPublic() || isLomBokClass
							|| (this.isProgramElementDocAnnotatedWith(fieldDoc, "lombok.Getter")
									&& paramType == ParameterType.Response)
							|| (this.isProgramElementDocAnnotatedWith(fieldDoc, "lombok.Setter")
									&& paramType == ParameterType.Request))) {
				APIParameter param = new APIParameter();
				param.setName(fieldDoc.name());
				param.setType(this.getTypeName(fieldDoc.type(), false));
				if (!processingClasses.contains(fieldDoc.type().qualifiedTypeName())) {
					param.setFields(this.getFields(fieldDoc.type(), paramType, processingClasses));
				}
				param.setDescription(this.getFieldDescription(fieldDoc));
				param.setExample(this.getMemberExample(fieldDoc));
				param.setHistory(new ModificationHistory(this.parseModificationRecords(fieldDoc.tags())));
				param.setParameterOccurs(this.parseParameterOccurs(fieldDoc.tags(WROccursTaglet.NAME)));
				result.add(param);
			} else {
				privateFieldDesc.put(fieldDoc.name(), fieldDoc.commentText());
				Example example = this.getMemberExample(fieldDoc);
				if (example != null) {
					privateFieldExample.put(fieldDoc.name(), example);			
				}
				String jsonField = this.getJsonField(fieldDoc);
				if (jsonField != null) {
					privateJsonField.put(fieldDoc.name(), jsonField);
				}
				privateFieldValidator.put(fieldDoc.name(), this.getFieldValidatorDesc(fieldDoc));
				if (fieldDoc.isTransient()) {
					transientFieldSet.add(fieldDoc.name());
				}
			}
		}

		MethodDoc[] methodDocs = classDoc.methods(false);
		for (MethodDoc methodDoc : methodDocs) {
			if (transientFieldSet.contains(this.getFieldNameOfAccesser(methodDoc.name()))) {
				continue;
			}
			if ((paramType == ParameterType.Response && this.isGetterMethod(methodDoc))
					|| (paramType == ParameterType.Request && this.isSetterMethod(methodDoc))) {
				APIParameter param = new APIParameter();
				String fieldNameOfAccesser = this.getFieldNameOfAccesser(methodDoc.name());
				param.setName(fieldNameOfAccesser);
				String jsonField = this.getJsonField(methodDoc);
				if (jsonField != null) {
					param.setName(jsonField);
				} else if (privateJsonField.containsKey(param.getName())) {
					param.setName(privateJsonField.get(param.getName()));
				}
				Type typeToProcess = null;
				if (paramType == ParameterType.Request) {
					// set method only has one parameter.
					typeToProcess = methodDoc.parameters()[0].type();
				} else {
					typeToProcess = methodDoc.returnType();
				}
				param.setType(this.getTypeName(typeToProcess, false));
				if (!processingClasses.contains(typeToProcess.qualifiedTypeName())) {
					param.setFields(this.getFields(typeToProcess, paramType, processingClasses));
				}
				param.setHistory(new ModificationHistory(this.parseModificationRecords(methodDoc.tags())));
				if (StringUtils.isEmpty(methodDoc.commentText())) {
					if (paramType == ParameterType.Request) {
						param.setDescription(this.getParamComment(methodDoc, methodDoc.parameters()[0].name()));
					} else {
						for (Tag tag : methodDoc.tags("return")) {
							param.setDescription(tag.text());
						}
					}
				} else {
					param.setDescription(methodDoc.commentText());
				}

				if (StringUtils.isEmpty(param.getDescription())) {
					String temp = privateFieldDesc.get(param.getName());
					if (temp == null) {
						if (typeToProcess.typeName().equals("boolean")) {
							temp = privateFieldDesc.get(param.getName());
							if (temp == null) {
								param.setDescription(privateFieldDesc
										.get("is" + net.winroad.wrdoclet.utils.Util.capitalize(param.getName())));
							}
						}
					} else {
						param.setDescription(temp);
					}
				}

				if (privateFieldValidator.get(fieldNameOfAccesser) != null) {
					param.setDescription(param.getDescription() == null ? privateFieldValidator.get(fieldNameOfAccesser)
							: param.getDescription() + " " + privateFieldValidator.get(fieldNameOfAccesser));
				}

				Example example = this.getMemberExample(methodDoc);
				if (example == null) {
					if(privateFieldExample.containsKey(param.getName())) {
						param.setExample(privateFieldExample.get(param.getName()));
					}
				} else {
					param.setExample(example);
				}
				param.setParameterOccurs(this.parseParameterOccurs(methodDoc.tags(WROccursTaglet.NAME)));
				result.add(param);
			}
		}
		return result;
	}

	protected String getFieldDescription(FieldDoc fieldDoc) {
		StringBuilder strBuilder = new StringBuilder();
		strBuilder.append(fieldDoc.commentText());
		strBuilder.append(" ");
		strBuilder.append(this.getFieldValidatorDesc(fieldDoc));
		return strBuilder.toString();
	}

	protected SimpleEntry<String, String> parseParamTag(String text) {
		text = StringUtils.strip(text);
		int index = text.indexOf(" ");
		if (index > 0) {
			String paramName = text.substring(0, index);
			String comment = text.substring(index);
			SimpleEntry<String, String> result = new AbstractMap.SimpleEntry<String, String>(
					paramName, StringUtils.strip(comment));
			return result;			
		}
		return null;
	}
	
	protected Example getExample(MethodDoc methodDoc, Parameter parameter) {
		Example result = new Example();
		boolean hasExample = false;
		for(Tag tag : methodDoc.tags(WREgnameTaglet.NAME)) {
			SimpleEntry<String, String> p = this.parseParamTag(tag.text());
			if (p != null && parameter.name().equals(p.getKey())) {
				result.setName(p.getValue());
				hasExample = true;
			}
		}
		for(Tag tag : methodDoc.tags(WREgsummaryTaglet.NAME)) {
			SimpleEntry<String, String> p = this.parseParamTag(tag.text());
			if (p != null && parameter.name().equals(p.getKey())) {
				result.setSummary(p.getValue());
				hasExample = true;
			}
		}
		for(Tag tag : methodDoc.tags(WREgvalueTaglet.NAME)) {
			SimpleEntry<String, String> p = this.parseParamTag(tag.text());
			if (p != null && parameter.name().equals(p.getKey())) {
				result.setValue(p.getValue());
				hasExample = true;
			}
		}
		for(Tag tag : methodDoc.tags(WREgexternalvalueTaglet.NAME)) {
			SimpleEntry<String, String> p = this.parseParamTag(tag.text());
			if (p != null && parameter.name().equals(p.getKey())) {
				result.setExternalValue(p.getValue());
				hasExample = true;
			}
		}
		for(Tag tag : methodDoc.tags(WREgdescriptionTaglet.NAME)) {
			SimpleEntry<String, String> p = this.parseParamTag(tag.text());
			if (p != null && parameter.name().equals(p.getKey())) {
				result.setDescription(p.getValue());
				hasExample = true;
			}
		}
		if(hasExample) {
			return result;
		}
		return null;
	}
	
	protected Example getMemberExample(MemberDoc memberDoc) {
		Example result = new Example();
		boolean hasExample = false;
		for(Tag tag : memberDoc.tags(WREgnameTaglet.NAME)) {
			result.setName(tag.text());
			hasExample = true;
		}
		for(Tag tag : memberDoc.tags(WREgsummaryTaglet.NAME)) {
			result.setSummary(tag.text());
			hasExample = true;
		}
		for(Tag tag : memberDoc.tags(WREgvalueTaglet.NAME)) {
			result.setValue(tag.text());
			hasExample = true;
		}
		for(Tag tag : memberDoc.tags(WREgexternalvalueTaglet.NAME)) {
			result.setExternalValue(tag.text());
			hasExample = true;
		}
		for(Tag tag : memberDoc.tags(WREgdescriptionTaglet.NAME)) {
			result.setDescription(tag.text());
			hasExample = true;
		}
		if(hasExample) {
			return result;
		}
		return null;
	}
	
	protected String getJsonField(MemberDoc memberDoc) {
		for (AnnotationDesc annotationDesc : memberDoc.annotations()) {
			if (annotationDesc.annotationType().qualifiedTypeName()
					.startsWith("com.fasterxml.jackson.annotation.JsonProperty")) {
				if (annotationDesc.elementValues().length > 0) {
					for (AnnotationDesc.ElementValuePair elementValuePair : annotationDesc.elementValues()) {
						if (elementValuePair.element().name().equals("value")
								&& !StringUtils.isEmpty(elementValuePair.value().toString())
								&& !"\"\"".equals(elementValuePair.value().toString())) {
							return elementValuePair.value().toString().replace("\"", "");
						}
					}
				}
			}
			if (annotationDesc.annotationType().qualifiedTypeName()
					.startsWith("com.alibaba.fastjson.annotation.JSONField")) {
				if (annotationDesc.elementValues().length > 0) {
					for (AnnotationDesc.ElementValuePair elementValuePair : annotationDesc.elementValues()) {
						if (elementValuePair.element().name().equals("name")
								&& !StringUtils.isEmpty(elementValuePair.value().toString())
								&& !"\"\"".equals(elementValuePair.value().toString())) {
							return elementValuePair.value().toString().replace("\"", "");
						}
					}
				}
			}
		}
		return null;
	}

	protected String getSimpleAnnotationStr(AnnotationDesc annotationDesc) {
		StringBuilder strBuilder = new StringBuilder();
		strBuilder.append("@");
		strBuilder.append(annotationDesc.annotationType().name());
		if (annotationDesc.elementValues().length > 0) {
			strBuilder.append("(");
			boolean isFirstElement = true;
			for (AnnotationDesc.ElementValuePair elementValuePair : annotationDesc.elementValues()) {
				if (!isFirstElement) {
					strBuilder.append(",");
				}
				strBuilder.append(elementValuePair.element().name());
				strBuilder.append("=");
				strBuilder.append(
						net.winroad.wrdoclet.utils.Util.decodeUnicode(elementValuePair.value().toString()));
				isFirstElement = false;
			}
			strBuilder.append(")");
		}
		return strBuilder.toString();
	}

	protected String getFieldValidatorDesc(FieldDoc fieldDoc) {
		StringBuilder strBuilder = new StringBuilder();
		for (AnnotationDesc annotationDesc : fieldDoc.annotations()) {
			if (annotationDesc.annotationType().qualifiedTypeName().startsWith("org.hibernate.validator.constraints")
					|| annotationDesc.annotationType().qualifiedTypeName().startsWith("javax.validation.constraints")
					|| annotationDesc.annotationType().qualifiedTypeName().startsWith("lombok.NonNull")) {
				strBuilder.append(this.getSimpleAnnotationStr(annotationDesc));
				strBuilder.append(" ");
			}
		}
		return strBuilder.toString();
	}

	protected String getTypeName(Type typeToProcess, boolean ignoreSuperType) {
		// special type to process e.g. java.util.Map.Entry<Address,Person>
		ParameterizedType pt = typeToProcess.asParameterizedType();
		if (pt != null && pt.typeArguments().length > 0) {
			StringBuilder strBuilder = new StringBuilder();
			strBuilder.append(typeToProcess.qualifiedTypeName());
			strBuilder.append("<");
			for (Type arg : pt.typeArguments()) {
				strBuilder.append(this.getTypeName(arg, true));
				strBuilder.append(",");
			}
			int len = strBuilder.length();
			// trim the last ","
			strBuilder.deleteCharAt(len - 1);
			strBuilder.append(">");
			return strBuilder.toString();
		}

		if (typeToProcess.asClassDoc() != null) {
			ClassDoc superClass = typeToProcess.asClassDoc().superclass();
			if (superClass != null) {
				// handle enum to output enum values into doc
				if ("java.lang.Enum".equals(superClass.qualifiedTypeName())) {
					FieldDoc[] enumConstants = typeToProcess.asClassDoc().enumConstants();
					StringBuilder strBuilder = new StringBuilder();
					strBuilder.append("Enum[");
					if(enumConstants.length > 0) {
						for (FieldDoc enumConstant : enumConstants) {
							strBuilder.append(enumConstant.name());
							strBuilder.append(",");
						}
						int len = strBuilder.length();
						// trim the last ","
						strBuilder.deleteCharAt(len - 1);
					}
					strBuilder.append("]");
					return strBuilder.toString();
				} else if (!ignoreSuperType && !this.isInStopClasses(superClass)) {
					if(typeToProcess.qualifiedTypeName().equals("T") //e.g. class BankAlliance<T extends Bank> extends BaseAlliance
							|| "[]".equals(typeToProcess.dimension()) // e.g. Clazz[] relatedClasses
							) {
						return typeToProcess.toString() + " extends "
								+ this.getTypeName(typeToProcess.asClassDoc().superclassType(), false);
					} else {
						return typeToProcess.asClassDoc().toString() + " extends "
								+ this.getTypeName(typeToProcess.asClassDoc().superclassType(), false);
					}
				}
			}

			if(typeToProcess.qualifiedTypeName().equals("T") //e.g. class BankAlliance<T extends Bank> extends BaseAlliance
					|| "[]".equals(typeToProcess.dimension()) // e.g. Clazz[] relatedClasses
					) {
				return typeToProcess.toString();
			} else {
				return typeToProcess.asClassDoc().toString();
			}
		} else {
			return typeToProcess.toString();
		}
	}

	/*
	 * Parse the ParameterOccurs from the tags.
	 */
	protected ParameterOccurs parseParameterOccurs(Tag[] tags) {
		for (int i = 0; i < tags.length; i++) {
			if (("@" + WROccursTaglet.NAME).equalsIgnoreCase(tags[i].name())) {
				if (WROccursTaglet.REQUIRED.equalsIgnoreCase(tags[i].text())) {
					return ParameterOccurs.REQUIRED;
				} else if (WROccursTaglet.OPTIONAL.equalsIgnoreCase(tags[i].text())) {
					return ParameterOccurs.OPTIONAL;
				} else if (WROccursTaglet.DEPENDS.equalsIgnoreCase(tags[i].text())) {
					return ParameterOccurs.DEPENDS;
				} else {
					this.logger.warn("Unexpected WROccursTaglet: " + tags[i].text());
				}
			}
		}
		return null;
	}

	/*
	 * is the method a getter method of a field.
	 */
	protected boolean isGetterMethod(MethodDoc methodDoc) {
		if (methodDoc.parameters() != null && methodDoc.parameters().length == 0
				&& (!"boolean".equalsIgnoreCase(methodDoc.returnType().qualifiedTypeName())
						&& methodDoc.name().matches("^get.+"))
				|| (("boolean".equalsIgnoreCase(methodDoc.returnType().qualifiedTypeName())
						&& methodDoc.name().matches("^is.+")))) {
			return true;
		}
		return false;
	}

	/*
	 * is the method a setter method of a field.
	 */
	protected boolean isSetterMethod(MethodDoc methodDoc) {
		if (methodDoc.parameters() != null && methodDoc.parameters().length == 1
				&& methodDoc.name().matches("^set.+")) {
			return true;
		}
		return false;
	}

	/*
	 * get the field name which the getter or setter method to access. NOTE: the
	 * getter or setter method name should follow the naming convention.
	 */
	protected String getFieldNameOfAccesser(String methodName) {
		if (methodName.startsWith("get")) {
			return net.winroad.wrdoclet.utils.Util.uncapitalize(methodName.replaceFirst("get", ""));
		} else if (methodName.startsWith("set")) {
			return net.winroad.wrdoclet.utils.Util.uncapitalize(methodName.replaceFirst("set", ""));
		} else {
			return net.winroad.wrdoclet.utils.Util.uncapitalize(methodName.replaceFirst("is", ""));
		}
	}

	public static Document readXMLConfig(String filePath)
			throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
		builderFactory.setNamespaceAware(true);
		DocumentBuilder builder = builderFactory.newDocumentBuilder();
		File dubboConfig = new File(filePath);
		return builder.parse(dubboConfig);
	}

	public static String getAttributeValue(Node node, String attributeName) {
		NamedNodeMap attributes = node.getAttributes();
		if (attributes != null) {
			Node attribute = attributes.getNamedItem(attributeName);
			if (attribute != null) {
				return attribute.getNodeValue();
			}
		}
		return null;
	}

	protected void processAnnotations(AnnotationDesc annotation, APIParameter apiParameter) {
		if (annotation.annotationType().qualifiedName().startsWith("org.springframework.web.bind.annotation.")) {
			for (ElementValuePair pair : annotation.elementValues()) {
				if (pair.element().name().equals("value") || pair.element().name().equals("name")) {
					if (pair.value() != null) {
						apiParameter.setName(pair.value().toString().replace("\"", ""));
					}
				}
				if (pair.element().name().equals("required")) {
					if (pair.value().value().equals(true)) {
						apiParameter.setParameterOccurs(ParameterOccurs.REQUIRED);
					} else {
						apiParameter.setParameterOccurs(ParameterOccurs.OPTIONAL);
					}
				}
			}
			if(annotation.annotationType().qualifiedName().equals("org.springframework.web.bind.annotation.PathVariable")) {
				apiParameter.setParameterLocation(ParameterLocation.PATH);
			} else if(annotation.annotationType().qualifiedName().equals("org.springframework.web.bind.annotation.CookieValue")) {
				apiParameter.setParameterLocation(ParameterLocation.COOKIE);
			} else if(annotation.annotationType().qualifiedName().equals("org.springframework.web.bind.annotation.RequestHeader")) {
				apiParameter.setParameterLocation(ParameterLocation.HEADER);
			} else if(annotation.annotationType().qualifiedName().equals("org.springframework.web.bind.annotation.RequestBody")) {
				apiParameter.setParameterLocation(ParameterLocation.BODY);
			} else if(annotation.annotationType().qualifiedName().equals("org.springframework.web.bind.annotation.RequestParam")) {
				apiParameter.setParameterLocation(ParameterLocation.QUERY);
			}
		}
	}

	protected void handleRefReq(MethodDoc method, List<APIParameter> paramList) {
		Tag[] tags = method.tags(WRRefReqTaglet.NAME);
		for (int i = 0; i < tags.length; i++) {
			APIParameter apiParameter = new APIParameter();
			String[] strArr = tags[i].text().split(" ");
			for (int j = 0; j < strArr.length; j++) {
				switch (j) {
				case 0:
					apiParameter.setName(strArr[j]);
					break;
				case 1:
					apiParameter.setType(strArr[j]);
					break;
				case 2:
					apiParameter.setDescription(strArr[j]);
					break;
				case 3:
					if (StringUtils.equalsIgnoreCase(strArr[j], WROccursTaglet.REQUIRED)) {
						apiParameter.setParameterOccurs(ParameterOccurs.REQUIRED);
					} else if (StringUtils.equalsIgnoreCase(strArr[j], WROccursTaglet.OPTIONAL)) {
						apiParameter.setParameterOccurs(ParameterOccurs.OPTIONAL);
					}
					break;
				default:
					logger.warn("Unexpected tag:" + tags[i].text());
				}
			}
			HashSet<String> processingClasses = new HashSet<String>();
			ClassDoc c = this.wrDoc.getConfiguration().root.classNamed(apiParameter.getType());
			if (c != null) {
				apiParameter.setFields(this.getFields(c, ParameterType.Request, processingClasses));
			}
			paramList.add(apiParameter);
		}
	}

}
