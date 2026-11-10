package com.aquasense.audit;

import java.util.Map;

/** Readable names of the audit actions. An action without a name is shown with its code. */
public final class ActionLabels {

    public static final Map<String, String> ALL = Map.ofEntries(
        Map.entry("USER_REGISTERED", "Creó su cuenta"),
        Map.entry("USER_LOGIN", "Inició sesión"),
        Map.entry("USER_LOGOUT", "Cerró sesión"),
        Map.entry("LOGIN_BLOCKED", "Intento de ingreso con la cuenta desactivada"),
        Map.entry("PASSWORD_RESET_REQUESTED", "Pidió restablecer la contraseña"),
        Map.entry("PASSWORD_RESET", "Restableció la contraseña"),
        Map.entry("SETTINGS_UPDATED", "Cambió su configuración"),
        Map.entry("MEASUREMENT_SAVED", "Guardó una medición"),
        Map.entry("MEASUREMENT_DELETED", "Borró una medición"),
        Map.entry("MEASUREMENTS_EXPORTED", "Descargó sus mediciones"),
        Map.entry("PROFILE_CREATED", "Creó un perfil de agua"),
        Map.entry("PROFILE_UPDATED", "Editó un perfil de agua"),
        Map.entry("PROFILE_DELETED", "Borró un perfil de agua"),
        Map.entry("PROFILE_DUPLICATED", "Duplicó un perfil de agua"),
        Map.entry("PROFILE_ACTIVATED", "Cambió el perfil activo"),
        Map.entry("REPORT_GENERATED", "Generó un informe"),
        Map.entry("AGENT_REPORT", "Pidió un informe al agente de IA"),
        Map.entry("AI_DIAGNOSIS", "Pidió un diagnóstico con IA"),
        Map.entry("CHAT_QUESTION", "Preguntó al asistente de IA"),
        Map.entry("SESSIONS_CLOSED", "Cerró sus otras sesiones"),
        Map.entry("PAYMENT_STARTED", "Inició un pago"),
        Map.entry("PREMIUM_PAID", "Pagó Premium"),
        Map.entry("PAYMENT_DECLINED", "Pago rechazado"),
        Map.entry("PAYMENT_MISMATCH", "Pago con datos que no coinciden"),
        Map.entry("PREMIUM_EXPIRED", "Venció su Premium"),
        Map.entry("ADMIN_VERIFICATION_SENT", "Se envió el enlace para confirmar el rol de administrador"),
        Map.entry("ADMIN_ROLE_CONFIRMED", "Confirmó el rol de administrador por correo"),
        Map.entry("ADMIN_VERIFICATION_FAILED", "No se pudo enviar el enlace para confirmar el rol de administrador"),
        Map.entry("ADMIN_PLAN_CHANGED", "Cambió el plan de un usuario"),
        Map.entry("ADMIN_ROLE_CHANGED", "Cambió el rol de un usuario"),
        Map.entry("ADMIN_USER_DISABLED", "Desactivó una cuenta"),
        Map.entry("ADMIN_USER_ENABLED", "Reactivó una cuenta"),
        Map.entry("ADMIN_USER_DELETED", "Eliminó una cuenta"),
        Map.entry("ACCOUNT_PLAN_CHANGED", "Un administrador cambió su plan"),
        Map.entry("ACCOUNT_ROLE_CHANGED", "Un administrador cambió su rol"),
        Map.entry("ACCOUNT_DISABLED", "Un administrador desactivó la cuenta"),
        Map.entry("ACCOUNT_ENABLED", "Un administrador reactivó la cuenta"),
        Map.entry("MAIL_TEST_SENT", "Envió un correo de prueba"),
        Map.entry("MAIL_TEST_FAILED", "Falló el correo de prueba"));

    private ActionLabels() {
    }

    public static String of(String action) {
        return ALL.getOrDefault(action, action);
    }
}
