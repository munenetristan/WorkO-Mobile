import React, { createContext, useCallback, useMemo, useState } from 'react';

export type CountryOption = {
  id: string;
  name: string;
  iso2: string;
  dialCode: string;
  flag: string;
};

export type AuthState = {
  authToken: string | null;
  otpToken: string | null;
  country: CountryOption | null;
  phoneNumber: string;
  role: 'customer' | 'provider' | null;
};

export const AuthContext = createContext<{
  state: AuthState;
  setCountry: (country: CountryOption) => void;
  setPhoneNumber: (phone: string) => void;
  setOtpToken: (token: string | null) => void;
  setRole: (role: AuthState['role']) => void;
  setAuthToken: (token: string | null) => Promise<void>;
} | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [authToken, setAuthTokenState] = useState<string | null>(null);
  const [otpToken, setOtpToken] = useState<string | null>(null);
  const [country, setCountry] = useState<CountryOption | null>(null);
  const [phoneNumber, setPhoneNumber] = useState('');
  const [role, setRole] = useState<AuthState['role']>(null);

  const setAuthToken = useCallback(async (token: string | null) => {
    setAuthTokenState(token);
  }, []);

  const value = useMemo(
    () => ({
      state: {
        authToken,
        otpToken,
        country,
        phoneNumber,
        role,
      },
      setCountry,
      setPhoneNumber,
      setOtpToken,
      setRole,
      setAuthToken,
    }),
    [authToken, otpToken, country, phoneNumber, role, setAuthToken]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
