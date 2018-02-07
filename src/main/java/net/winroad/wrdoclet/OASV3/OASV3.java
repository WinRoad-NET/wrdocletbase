package net.winroad.wrdoclet.OASV3;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;

@Getter
@Setter
public class OASV3 {
    private String openapi = "3.0.0";
    private Info info;
    HashMap<String, PathItem> paths;
    private Components components;
    private List<Tag> tags;
}
