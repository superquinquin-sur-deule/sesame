export type OrvalResponse<T> = {
  status: number;
  data: T;
  headers: Headers;
};

export const fetcher = async <T>(url: string, init?: RequestInit): Promise<T> => {
  const resp = await fetch(url, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
  });
  const text = await resp.text();
  let data: unknown;
  try {
    data = text ? JSON.parse(text) : undefined;
  } catch {
    data = text;
  }
  if (!resp.ok) {
    throw Object.assign(new Error(`HTTP ${resp.status}`), {
      status: resp.status,
      data,
    });
  }
  return {
    status: resp.status,
    data,
    headers: resp.headers,
  } as T;
};

export default fetcher;
