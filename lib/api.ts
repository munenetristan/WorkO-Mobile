export type ApiError = {
  message: string;
  status?: number;
};

export async function apiRequest<T>(path: string, options: RequestInit = {}) {
  const baseUrl = process.env.EXPO_PUBLIC_API_URL;
  if (!baseUrl) {
    throw new Error('EXPO_PUBLIC_API_URL is not set');
  }

  const response = await fetch(`${baseUrl}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers ?? {}),
    },
    ...options,
  });

  const contentType = response.headers.get('content-type');
  const isJson = contentType?.includes('application/json');
  const data = isJson ? await response.json() : null;

  if (!response.ok) {
    const message = data?.message ?? 'Request failed';
    const error: ApiError = { message, status: response.status };
    throw error;
  }

  return data as T;
}

export async function apiRequestWithAuth<T>(
  path: string,
  token: string,
  options: RequestInit = {}
) {
  return apiRequest<T>(path, {
    ...options,
    headers: {
      ...(options.headers ?? {}),
      Authorization: `Bearer ${token}`,
    },
  });
}
