{{- define "taskmigo.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "taskmigo.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "taskmigo.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "taskmigo.labels" -}}
helm.sh/chart: {{ include "taskmigo.chart" . }}
app.kubernetes.io/name: {{ include "taskmigo.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "taskmigo.selectorLabels" -}}
app.kubernetes.io/name: {{ include "taskmigo.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "taskmigo.serviceAccountName" -}}
{{- default "default" .Values.serviceAccountName }}
{{- end }}

{{- define "taskmigo.webUrl" -}}
{{- default (printf "http://%s-web:%v" (include "taskmigo.fullname" .) .Values.web.service.port) .Values.web.publicUrl }}
{{- end }}

{{- define "taskmigo.clientUrl" -}}
{{- default (printf "http://%s-client:%v" (include "taskmigo.fullname" .) .Values.client.service.port) .Values.client.publicUrl }}
{{- end }}

{{- define "taskmigo.clientIssuer" -}}
{{- default (include "taskmigo.webUrl" .) .Values.client.auth.issuer }}
{{- end }}

{{- define "taskmigo.gatewayName" -}}
{{- if .Values.gateway.create -}}
{{- default (include "taskmigo.fullname" .) .Values.gateway.name | trunc 63 | trimSuffix "-" }}
{{- else -}}
{{- required "gateway.name is required when gateway.enabled=true and gateway.create=false" .Values.gateway.name | trunc 63 | trimSuffix "-" }}
{{- end -}}
{{- end }}

{{- define "taskmigo.databaseEnv" -}}
- name: TASKMIGO_DATABASE_URL
  value: {{ .Values.database.url | quote }}
- name: TASKMIGO_DATABASE_USERNAME
  value: {{ .Values.database.username | quote }}
- name: TASKMIGO_DATABASE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .Values.database.existingSecret | quote }}
      key: {{ .Values.database.passwordKey | quote }}
{{- end }}
