package net.winroad.wrdoclet.OASV3;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Info {
    /**
     * REQUIRED. The title of the application.
     */
    private String title;
    /**
     * A short description of the application. CommonMark syntax MAY be used for rich text representation.
     */
    private String description;
    private String termsOfService;
    private Contact contact;
    /**
     * REQUIRED. The version of the OpenAPI document (which is distinct from the OpenAPI Specification version or the API implementation version).
     */
    private String version;
}
