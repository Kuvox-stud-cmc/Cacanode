CREATE INDEX idx_recruitment_jobs_platform_status_updated
    ON recruitment_jobs(status, updated_at DESC, id);
CREATE INDEX idx_recruitment_jobs_platform_closing
    ON recruitment_jobs(closing_at, id);
CREATE INDEX idx_recruitment_jobs_platform_published
    ON recruitment_jobs(published_at DESC, id);
CREATE INDEX idx_recruitment_jobs_platform_language_updated
    ON recruitment_jobs(language, updated_at DESC, id);
CREATE INDEX idx_recruitment_jobs_platform_employment_updated
    ON recruitment_jobs(employment_type, updated_at DESC, id);
CREATE INDEX idx_recruitment_jobs_platform_work_mode_updated
    ON recruitment_jobs(work_mode, updated_at DESC, id);
CREATE INDEX idx_recruitment_jobs_platform_department_updated
    ON recruitment_jobs(department, updated_at DESC, id);
CREATE INDEX idx_recruitment_jobs_platform_location_updated
    ON recruitment_jobs(location, updated_at DESC, id);
CREATE INDEX idx_recruitment_jobs_platform_title
    ON recruitment_jobs(lower(title), id);
CREATE INDEX idx_recruitment_jobs_platform_company
    ON recruitment_jobs(lower(frozen_company_name), id);
CREATE INDEX idx_recruitment_jobs_platform_metadata_search
    ON recruitment_jobs USING gin (lower(
        coalesce(title,'') || ' ' || coalesce(frozen_company_name,'') || ' ' ||
        coalesce(department,'') || ' ' || coalesce(location,'')
    ) gin_trgm_ops);
