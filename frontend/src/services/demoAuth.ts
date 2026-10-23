import axios from 'axios';

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8084',
});
export async function submitDemoLogin(username: string, password: string): Promise<string> {
  const response = await client.post<{ message: string }>('/api/demo/auth/login', {
    username,
    password,
  });
  return response.data.message;
}
