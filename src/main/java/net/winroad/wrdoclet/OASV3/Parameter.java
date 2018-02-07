package net.winroad.wrdoclet.OASV3;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Parameter extends Reference {
    private String name;
    private String in;
    private String description;
    private boolean required;
    private Schema schema;
}
