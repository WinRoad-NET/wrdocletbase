package net.winroad.wrdoclet.OASV3;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;

@Getter
@Setter
public class Schema extends Reference{
    private String type;
    private Schema items;
    private String format;
    private Long maximum;
    private Long exclusiveMaximum;
    private Long minimum;
    private Long exclusiveMinimum;
    private Long maxLength;
    private Long minLength;
    private String pattern;
    private Boolean required;

    @JsonProperty("enum")
    private List<String> enumField;

    private HashMap<String, Schema> properties;
}
