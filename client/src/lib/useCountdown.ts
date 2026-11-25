import { useEffect, useRef, useState } from "react";

/**
 * Whole seconds left on a countdown, ticking once a second. `start(seconds, onDone)` restarts it;
 * `onDone` runs once when it reaches zero.
 */
export function useCountdown() {
  const [deadline, setDeadline] = useState(0);
  const [now, setNow] = useState(0);
  const onDoneRef = useRef<(() => void) | undefined>(undefined);

  useEffect(() => {
    if (deadline === 0) return;
    const timer = setInterval(() => {
      const current = Date.now();
      setNow(current);
      if (current >= deadline) {
        clearInterval(timer);
        onDoneRef.current?.();
      }
    }, 1000);
    return () => clearInterval(timer);
  }, [deadline]);

  function start(seconds: number, onDone?: () => void) {
    const current = Date.now();
    onDoneRef.current = onDone;
    setNow(current);
    setDeadline(current + seconds * 1000);
  }

  return [Math.max(0, Math.ceil((deadline - now) / 1000)), start] as const;
}
