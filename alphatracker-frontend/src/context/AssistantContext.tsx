import React, { useCallback, useMemo, useState } from 'react';
import { useAuth } from './AuthContext';
import { AssistantContext } from './assistantContextValue';
import { useAssistantChat } from '../hooks/useAssistantChat';
import { subjectOf } from '../utils/assistantChat';

// Mounted ABOVE the routes in App.tsx, on purpose. Dashboard and Journal each render their own Sidebar,
// so state kept inside a page would reset every time you moved between them. Here, the conversation
// and the open/closed state survive navigation.
export const AssistantProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { token, loading } = useAuth();
  const owner = loading ? undefined : subjectOf(token); // see useAssistantChat: undefined means "not known yet"
  const chat = useAssistantChat(owner);
  const [isOpen, setIsOpen] = useState(false);

  // Signing out closes the panel along with wiping the chat. This is React's documented way to reset state
  // when a value changes: compare with the previous value DURING render, instead of setting state in an
  // effect (which would render once with the stale value, then again).
  const [previousOwner, setPreviousOwner] = useState(owner);
  if (owner !== previousOwner) {
    setPreviousOwner(owner);
    if (owner === null) setIsOpen(false);
  }

  const open = useCallback(() => setIsOpen(true), []);
  const close = useCallback(() => setIsOpen(false), []);
  const toggle = useCallback(() => setIsOpen((v) => !v), []);

  const value = useMemo(() => ({ ...chat, isOpen, open, close, toggle }), [chat, isOpen, open, close, toggle]);

  return <AssistantContext.Provider value={value}>{children}</AssistantContext.Provider>;
};
