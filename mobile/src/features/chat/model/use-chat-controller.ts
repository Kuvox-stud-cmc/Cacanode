import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react';

import {
  useCreateChatSessionMutation,
  useHidePlaygroundSessionMutation,
  useLazyGetChatHistoryQuery,
  useListPlaygroundSessionsQuery,
  useSubmitChatMessageMutation,
} from '@/features/chat/api/chat-api';
import { useGetTenantWorkspaceQuery } from '@/features/chat/api/workspace-api';
import {
  canSendMessage,
  chatFailureMessage,
  chatReducer,
  initialChatState,
  isSessionNotFound,
  workspaceFailureMessage,
} from '@/features/chat/model/chat-state';
import type { PlaygroundSession } from '@/features/chat/types';
import type { ApiError } from '@/services/api/errors';

let localId = 0;

function nextLocalId(prefix: string): string {
  localId += 1;
  return `${prefix}-${Date.now()}-${localId}`;
}

export function useChatController() {
  const [state, dispatch] = useReducer(chatReducer, initialChatState);
  const [hideError, setHideError] = useState<string | null>(null);
  const workspaceQuery = useGetTenantWorkspaceQuery();
  const sessionsQuery = useListPlaygroundSessionsQuery();
  const [loadHistory] = useLazyGetChatHistoryQuery();
  const [createSession] = useCreateChatSessionMutation();
  const [submitMessage] = useSubmitChatMessageMutation();
  const [hideSessionMutation, hideMutationState] = useHidePlaygroundSessionMutation();

  const sessions = useMemo(
    () =>
      (sessionsQuery.data ?? []).filter(
        (session) => !state.unavailableSessionIds.includes(session.id),
      ),
    [sessionsQuery.data, state.unavailableSessionIds],
  );

  useEffect(() => {
    if (workspaceQuery.data && sessionsQuery.data) {
      dispatch({ type: 'sessionsLoaded', sessions: sessionsQuery.data });
    }
  }, [sessionsQuery.data, workspaceQuery.data]);

  const reloadTranscript = useCallback(async (sessionId = state.selectedSessionId) => {
    if (!sessionId) return;
    const requestId = nextLocalId('history');
    dispatch({ type: 'historyStarted', sessionId, requestId });
    try {
      const messages = await loadHistory(sessionId, false).unwrap();
      dispatch({ type: 'historySucceeded', sessionId, requestId, messages });
    } catch (unknownError) {
      const error = unknownError as ApiError;
      const inaccessible = isSessionNotFound(error);
      dispatch({
        type: 'historyFailed',
        sessionId,
        requestId,
        inaccessible,
        message: inaccessible
          ? 'This conversation is no longer available.'
          : chatFailureMessage(error),
      });
      if (inaccessible) void sessionsQuery.refetch();
    }
  }, [loadHistory, sessionsQuery, state.selectedSessionId]);

  const previousSessionId = useRef<string | null>(null);
  useEffect(() => {
    const sessionId = state.selectedSessionId;
    if (!workspaceQuery.data || !sessionId || previousSessionId.current === sessionId) return;
    previousSessionId.current = sessionId;
    void reloadTranscript(sessionId);
  }, [reloadTranscript, state.selectedSessionId, workspaceQuery.data]);

  const startNewChat = useCallback(() => {
    if (state.activeSendId) return;
    previousSessionId.current = null;
    dispatch({ type: 'newChat' });
  }, [state.activeSendId]);

  const selectSession = useCallback((sessionId: string) => {
    if (state.activeSendId || sessionId === state.selectedSessionId) return;
    dispatch({ type: 'sessionSelected', sessionId });
  }, [state.activeSendId, state.selectedSessionId]);

  const setDraft = useCallback((draft: string) => {
    dispatch({ type: 'draftChanged', draft });
  }, []);

  const send = useCallback(async () => {
    const workspace = workspaceQuery.data;
    const content = state.draft.trim();
    if (!workspace || !canSendMessage(state.draft, true, Boolean(state.activeSendId))) return;

    const sendId = nextLocalId('assistant');
    dispatch({ type: 'sendStarted', sendId, userId: nextLocalId('user'), content });

    let sessionId = state.selectedSessionId;
    try {
      if (!sessionId) {
        const session = await createSession(workspace).unwrap();
        sessionId = session.id;
        previousSessionId.current = session.id;
        dispatch({ type: 'sessionCreated', sendId, sessionId: session.id });
      }

      let response;
      try {
        response = await submitMessage({ sessionId, content }).unwrap();
      } catch (unknownError) {
        const error = unknownError as ApiError;
        if (!isSessionNotFound(error)) throw error;

        const replacement = await createSession(workspace).unwrap();
        sessionId = replacement.id;
        previousSessionId.current = replacement.id;
        dispatch({ type: 'sessionCreated', sendId, sessionId: replacement.id });
        response = await submitMessage({ sessionId: replacement.id, content }).unwrap();
      }

      dispatch({ type: 'sendSucceeded', sendId, response });
      void sessionsQuery.refetch();
    } catch (unknownError) {
      dispatch({
        type: 'sendFailed',
        sendId,
        message: chatFailureMessage(unknownError as ApiError),
      });
    }
  }, [
    createSession,
    sessionsQuery,
    state.activeSendId,
    state.draft,
    state.selectedSessionId,
    submitMessage,
    workspaceQuery.data,
  ]);

  const hideSession = useCallback(async (session: PlaygroundSession) => {
    if (state.activeSendId || hideMutationState.isLoading) return false;
    setHideError(null);
    try {
      await hideSessionMutation(session.id).unwrap();
      const remaining = sessions.filter((candidate) => candidate.id !== session.id);
      const nextSessionId = remaining[0]?.id ?? null;
      if (state.selectedSessionId === session.id) {
        previousSessionId.current = null;
      }
      dispatch({ type: 'sessionHidden', sessionId: session.id, nextSessionId });
      return true;
    } catch (unknownError) {
      setHideError(chatFailureMessage(unknownError as ApiError));
      return false;
    }
  }, [
    hideMutationState.isLoading,
    hideSessionMutation,
    sessions,
    state.activeSendId,
    state.selectedSessionId,
  ]);

  return {
    state,
    sessions,
    workspace: workspaceQuery.data,
    workspaceLoading: workspaceQuery.isLoading,
    workspaceError: workspaceQuery.error
      ? workspaceFailureMessage(workspaceQuery.error as ApiError)
      : null,
    retryWorkspace: workspaceQuery.refetch,
    sessionsLoading: sessionsQuery.isLoading || sessionsQuery.isFetching,
    sessionsError: sessionsQuery.error
      ? chatFailureMessage(sessionsQuery.error as ApiError)
      : null,
    retrySessions: sessionsQuery.refetch,
    sending: Boolean(state.activeSendId),
    canSend: canSendMessage(
      state.draft,
      Boolean(workspaceQuery.data) && !workspaceQuery.isLoading,
      Boolean(state.activeSendId),
    ),
    hideError,
    hiding: hideMutationState.isLoading,
    setDraft,
    startNewChat,
    selectSession,
    reloadTranscript,
    send,
    hideSession,
  };
}
