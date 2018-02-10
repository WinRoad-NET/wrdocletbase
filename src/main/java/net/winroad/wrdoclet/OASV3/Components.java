package net.winroad.wrdoclet.OASV3;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;

@Getter
@Setter
public class Components {
    private HashMap<String, Schema> schemas;
    private HashMap<String, Object> responses;
    private HashMap<String, Object> parameters;
    private HashMap<String, Object> examples;
    private HashMap<String, Object> requestBodies;
    private HashMap<String, Object> headers;
    private HashMap<String, Object> securitySchemes;
    private HashMap<String, Object> links;
    private HashMap<String, Object> callbacks;
}
