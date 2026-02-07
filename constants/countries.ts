import type { CountryOption } from '@/context/auth-context';

export const fallbackCountries: CountryOption[] = [
  { id: 'ng', name: 'Nigeria', iso2: 'NG', dialCode: '+234', flag: '🇳🇬' },
  { id: 'gh', name: 'Ghana', iso2: 'GH', dialCode: '+233', flag: '🇬🇭' },
  { id: 'ke', name: 'Kenya', iso2: 'KE', dialCode: '+254', flag: '🇰🇪' },
  { id: 'za', name: 'South Africa', iso2: 'ZA', dialCode: '+27', flag: '🇿🇦' },
  { id: 'us', name: 'United States', iso2: 'US', dialCode: '+1', flag: '🇺🇸' },
];
