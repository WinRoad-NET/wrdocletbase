package net.winroad.wrdoclet.OASV3;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Reference {
    @JsonProperty("$ref")
    private String ref;
}
