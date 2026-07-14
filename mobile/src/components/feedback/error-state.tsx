import { StateView } from '@/components/feedback/state-view';

type ErrorStateProps = {
  description?: string;
  onRetry?: () => void;
  retryLabel?: string;
  title?: string;
};

export function ErrorState({
  description = 'Something went wrong. Please try again.',
  onRetry,
  retryLabel = 'Try again',
  title = 'Unable to continue',
}: ErrorStateProps) {
  return (
    <StateView
      actionLabel={onRetry ? retryLabel : undefined}
      description={description}
      onAction={onRetry}
      title={title}
    />
  );
}
