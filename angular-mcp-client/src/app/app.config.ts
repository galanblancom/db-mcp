import { ApplicationConfig, InjectionToken } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';

export const MCP_BASE_URL = new InjectionToken<string>('MCP_BASE_URL');

export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(),
    { provide: MCP_BASE_URL, useValue: 'http://localhost:8081' }
  ]
};
