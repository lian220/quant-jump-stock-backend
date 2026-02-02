-- V11: Add audit columns to user_roles table
-- This migration adds assigned_at and assigned_by columns to track role assignments

-- =====================================================================
-- Add audit columns to user_roles
-- =====================================================================
ALTER TABLE user_roles
    ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMP DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS assigned_by BIGINT REFERENCES users(id) ON DELETE SET NULL;

-- Create index for performance
CREATE INDEX IF NOT EXISTS idx_user_roles_assigned_at ON user_roles(assigned_at);
CREATE INDEX IF NOT EXISTS idx_user_roles_assigned_by ON user_roles(assigned_by);

-- Update existing rows to have assigned_at value
UPDATE user_roles SET assigned_at = created_at WHERE assigned_at IS NULL;

-- Comments
COMMENT ON COLUMN user_roles.assigned_at IS 'Timestamp when the role was assigned';
COMMENT ON COLUMN user_roles.assigned_by IS 'User who assigned this role (nullable for system assignments)';
