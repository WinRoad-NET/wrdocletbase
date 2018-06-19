package net.winroad.wrdoclet.data;

import java.util.LinkedList;
import java.util.List;

import lombok.Data;

/*
 * It could be interface argument or field of object which should be listed in 
 * the document.
 */
@Data
public class APIParameter {
	private String name;
	private String description;
	private ParameterOccurs parameterOccurs;
	private ParameterLocation parameterLocation;
	private String type;
	private ModificationHistory history;
	private List<APIParameter> fields;
	private Example example;
	/**
	 * whether it's type argument for parent (the parent is generic type).
	 */
	private boolean isParentTypeArgument;

	/*
	 * @return Whether this parameter has modification on specified version. If
	 * no version specified, returns true.
	 */
	public boolean isModifiedOnVersion(String version) {
		if (version == null || version.isEmpty()) {
			return true;
		}
		if (this.history != null && this.history.isModifiedOnVersion(version)) {
			return true;
		}
		if (this.fields != null) {
			for (APIParameter param : this.fields) {
				if (param.isModifiedOnVersion(version)) {
					return true;
				}
			}
		}
		return false;
	}

	public void appendField(APIParameter field) {
		if (this.fields == null) {
			this.fields = new LinkedList<APIParameter>();
		}

		this.fields.add(field);
	}

}
