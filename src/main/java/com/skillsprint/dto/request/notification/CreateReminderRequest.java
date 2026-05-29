package com.skillsprint.dto.request.notification;

import com.skillsprint.enums.notification.ReminderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateReminderRequest {

    ReminderType reminderType = ReminderType.GENERAL;

    @NotBlank
    @Size(max = 1000)
    String message;

    @NotNull
    Instant scheduledAt;
}