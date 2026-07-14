import { StateView } from '@/components/feedback/state-view';

type LoadingStateProps = {
  description?: string;
  title?: string;
};

export function LoadingState({ description, title = 'Loading' }: LoadingStateProps) {
  return <StateView description={description} loading title={title} />;
}
