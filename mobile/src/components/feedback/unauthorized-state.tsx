import { StateView } from '@/components/feedback/state-view';
import { useTranslation } from 'react-i18next';

type UnauthorizedStateProps = {
  onAction?: () => void;
};

export function UnauthorizedState({ onAction }: UnauthorizedStateProps) {
  const {t}=useTranslation();
  return (
    <StateView
      actionLabel={onAction ? t('common.goBack') : undefined}
      description={t('feedback.permissionDenied')}
      onAction={onAction}
      title={t('feedback.accessDenied')}
    />
  );
}
