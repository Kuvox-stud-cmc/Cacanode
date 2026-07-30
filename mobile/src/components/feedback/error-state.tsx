import { StateView } from '@/components/feedback/state-view';
import { useTranslation } from 'react-i18next';

type ErrorStateProps = {
  description?: string;
  onRetry?: () => void;
  retryLabel?: string;
  title?: string;
};

export function ErrorState({
  description,
  onRetry,
  retryLabel,
  title,
}: ErrorStateProps) {
  const {t}=useTranslation();
  return (
    <StateView
      actionLabel={onRetry ? retryLabel??t('common.tryAgain') : undefined}
      description={description??t('feedback.errorDescription')}
      onAction={onRetry}
      title={title??t('feedback.errorTitle')}
    />
  );
}
