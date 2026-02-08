-- 삭제된 CombinedAnalysisJobAdapter에 대한 Quartz 레코드 정리
-- Core 시작 시 ClassNotFoundException 방지

DELETE FROM quartz_cron_triggers WHERE trigger_name = 'combinedAnalysisTrigger' AND trigger_group = 'DEFAULT';
DELETE FROM quartz_simple_triggers WHERE trigger_name = 'combinedAnalysisTrigger' AND trigger_group = 'DEFAULT';
DELETE FROM quartz_triggers WHERE job_name = 'combinedAnalysisJob' AND job_group = 'DEFAULT';
DELETE FROM quartz_job_details WHERE job_name = 'combinedAnalysisJob' AND job_group = 'DEFAULT';
