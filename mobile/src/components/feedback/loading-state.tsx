import { StateView } from '@/components/feedback/state-view';
import { useTranslation } from 'react-i18next';

type LoadingStateProps = {
  description?: string;
  title?: string;
};

export function LoadingState({ description, title }: LoadingStateProps) {
  const {t}=useTranslation();
  return <StateView description={description} loading title={title??t('feedback.loading')} />;
}
