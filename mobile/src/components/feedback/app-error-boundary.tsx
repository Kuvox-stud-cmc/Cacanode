import { Component, Fragment, type ErrorInfo, type PropsWithChildren } from 'react';

import { ErrorState } from '@/components/feedback/error-state';
import { Screen } from '@/components/layout/screen';

type AppErrorBoundaryState = {
  hasError: boolean;
  resetKey: number;
};

export class AppErrorBoundary extends Component<PropsWithChildren, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = {
    hasError: false,
    resetKey: 0,
  };

  static getDerivedStateFromError(): Partial<AppErrorBoundaryState> {
    return { hasError: true };
  }

  componentDidCatch(_error: Error, _info: ErrorInfo) {
    // Intentionally avoid logging render errors because error messages may contain sensitive data.
  }

  private reset = () => {
    this.setState((state) => ({ hasError: false, resetKey: state.resetKey + 1 }));
  };

  render() {
    if (this.state.hasError) {
      return (
        <Screen style={{ justifyContent: 'center' }}>
          <ErrorState onRetry={this.reset} />
        </Screen>
      );
    }

    return <Fragment key={this.state.resetKey}>{this.props.children}</Fragment>;
  }
}
