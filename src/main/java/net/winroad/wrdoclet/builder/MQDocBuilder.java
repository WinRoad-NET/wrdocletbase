package net.winroad.wrdoclet.builder;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.winroad.wrdoclet.data.APIParameter;
import net.winroad.wrdoclet.data.ParameterOccurs;
import net.winroad.wrdoclet.data.ParameterType;
import net.winroad.wrdoclet.data.RequestMapping;
import net.winroad.wrdoclet.data.WRDoc;
import net.winroad.wrdoclet.taglets.WRMqConsumerTaglet;
import net.winroad.wrdoclet.taglets.WRMqProducerTaglet;
import net.winroad.wrdoclet.taglets.WRTagTaglet;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.Tag;
import com.sun.tools.doclets.internal.toolkit.Configuration;
import com.sun.tools.doclets.internal.toolkit.util.Util;

public class MQDocBuilder extends AbstractDocBuilder {

	public MQDocBuilder(WRDoc wrDoc) {
		super(wrDoc);
	}

	@Override
	protected void processOpenAPIClasses(ClassDoc[] classes,
			Configuration configuration) {
		for (int i = 0; i < classes.length; i++) {
			if (configuration.nodeprecated
					&& (Util.isDeprecated(classes[i]) || Util
							.isDeprecated(classes[i].containingPackage()))) {
				continue;
			}
			if(this.isMQMessageClasses(classes[i])) {
				Tag[] classTagArray = classes[i].tags(WRTagTaglet.NAME);
				if(classTagArray.length == 0) {
					String tag = "MQ";
					this.wrDoc.getWRTags().add(tag);
					if (!this.taggedOpenAPIClasses.containsKey(tag)) {
						this.taggedOpenAPIClasses.put(tag, new HashSet<ClassDoc>());
					}
					this.taggedOpenAPIClasses.get(tag).add(classes[i]);					
				} else {
					for (int j = 0; j < classTagArray.length; j++) {
						Set<String> classTags = WRTagTaglet
								.getTagSet(classTagArray[j].text());
						this.wrDoc.getWRTags().addAll(classTags);
						for (Iterator<String> iter = classTags.iterator(); iter
								.hasNext();) {
							String tag = iter.next();
							if (!this.taggedOpenAPIClasses.containsKey(tag)) {
								this.taggedOpenAPIClasses
										.put(tag, new HashSet<ClassDoc>());
							}
							this.taggedOpenAPIClasses.get(tag).add(classes[i]);					
						}
					}
				}
			}
		}
	}
	
	protected boolean isMQMessageClasses(ClassDoc classDoc) {
		Tag[] t = classDoc.tags(WRMqConsumerTaglet.NAME);
		if(t.length > 0) {
			return true;
		}
		t = classDoc.tags(WRMqProducerTaglet.NAME);
		return t.length > 0;
	}

	@Override
	protected int isAPIAuthNeeded(String url) {
		return 0;
	}

	@Override
	protected boolean isOpenAPIMethod(MethodDoc methodDoc) {
		return false;
	}

	@Override
	protected RequestMapping parseRequestMapping(MethodDoc methodDoc) {
		return null;
	}

	@Override
	protected APIParameter getOutputParam(MethodDoc methodDoc) {
		return null;
	}

	@Override
	protected List<APIParameter> getInputParams(MethodDoc methodDoc) {
		return null;
	}

	@Override
	protected RequestMapping parseRequestMapping(ClassDoc classDoc) {
		RequestMapping mapping = new RequestMapping();
		if(classDoc.tags(WRMqConsumerTaglet.NAME).length > 0) {
			mapping.setUrl(this.getMQConsumerTopic(classDoc));
		} else {
			mapping.setUrl(this.getMQProducerTopic(classDoc));
		}
		mapping.setTooltip(classDoc.simpleTypeName());
		mapping.setContainerName(classDoc.simpleTypeName());
		return mapping;
	}

	@Override
	protected APIParameter getOutputParam(ClassDoc classDoc) {
		if(classDoc.tags(WRMqConsumerTaglet.NAME).length > 0) {
			return null;
		} else {
			APIParameter apiParameter = new APIParameter();
			apiParameter.setParameterOccurs(ParameterOccurs.REQUIRED);
			apiParameter.setType(classDoc.qualifiedTypeName());
			apiParameter.setName(classDoc.name());
			HashSet<String> processingClasses = new HashSet<String>();
			apiParameter.setFields(this.getFields(classDoc,
					ParameterType.Response, processingClasses, null, null));
			apiParameter.setDescription(classDoc.commentText());
			return apiParameter;
		}
	}

	@Override
	protected APIParameter getInputParams(ClassDoc classDoc) {
		if(classDoc.tags(WRMqConsumerTaglet.NAME).length > 0) {
			APIParameter apiParameter = new APIParameter();
			apiParameter.setParameterOccurs(ParameterOccurs.REQUIRED);
			apiParameter.setType(classDoc.qualifiedTypeName());
			apiParameter.setName(classDoc.name());
			HashSet<String> processingClasses = new HashSet<String>();
			apiParameter.setFields(this.getFields(classDoc,
					ParameterType.Request, processingClasses, null, null));
			apiParameter.setDescription(classDoc.commentText());
			return apiParameter;
		} else {
			return null;
		}
	}

}
