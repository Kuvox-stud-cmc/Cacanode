import { StateView } from '@/components/feedback/state-view';
import { useTranslation } from 'react-i18next';

type OfflineStateProps = {
  onRetry?: () => void;
};

export function OfflineState({ onRetry }: OfflineStateProps) {
  const {t}=useTranslation();
  return (
    <StateView
      actionLabel={onRetry ? t('common.tryAgain') : undefined}
      description={t('feedback.offlineDescription')}
      onAction={onRetry}
      title={t('feedback.offlineTitle')}
    />
  );
}
