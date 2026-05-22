package com.skillsprint.dto.request.material;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConfirmMaterialUploadRequest {

    @NotBlank
    @Size(max = 512)
    String objectKey;

    @NotBlank
    @Size(max = 500)
    String fileName;

    @NotBlank
    @Size(max = 150)
    String contentType;
}
