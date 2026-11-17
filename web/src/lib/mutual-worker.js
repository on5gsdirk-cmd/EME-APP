// Calculates the mutual windows off the main thread (#120), so the page does not freeze on a slow phone.
// Message in: the arguments of nextMutualWindows; message out: its result.
import { nextMutualWindows } from './mutual.js';

self.onmessage = (e) => self.postMessage(nextMutualWindows(...e.data));
