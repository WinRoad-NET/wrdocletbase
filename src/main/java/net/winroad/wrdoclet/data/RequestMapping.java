package net.winroad.wrdoclet.data;

import lombok.Data;

@Data
public class RequestMapping {
	private String url;
	private String methodType;
	private String tooltip;
	private String containerName;
	private String consumes;
	private String produces;
	private String headers;
	private String params;
}
