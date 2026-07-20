UPDATE billing_payment_orders pending
SET status = 'CANCELLED',
    failure_reason = 'Superseded by a successful payment',
    updated_at = NOW()
WHERE pending.status IN ('PENDING', 'PROCESSING')
  AND EXISTS (
      SELECT 1
      FROM billing_payment_orders paid
      WHERE paid.tenant_id = pending.tenant_id
        AND paid.status = 'PAID'
        AND paid.paid_at IS NOT NULL
        AND paid.paid_at >= pending.created_at
  );
