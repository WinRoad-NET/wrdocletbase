package net.winroad.wrdoclet.OASV3;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PathItem extends Reference{
    /**
     * An optional, string summary, intended to apply to all operations in this path.
     */
    private String summary;
    /**
     * An optional, string description, intended to apply to all operations in this path. CommonMark syntax MAY be used for rich text representation.
     */
    private String description;

    private Operation get;

    private Operation post;

    private Operation options;

    private List<Parameter> parameters;
}
