/// <reference types="cypress" />

declare namespace Cypress {
  interface Chainable {
    loginAs(email: string, password: string): Chainable<void>;
    mockAuthenticatedUser(): Chainable<void>;
  }
}

Cypress.Commands.add('loginAs', (email: string, password: string) => {
  cy.request('POST', '/api/v1/auth/login', { identifier: email, password })
    .then(res => {
      const auth = res.body.data;
      if (auth.totpRequired) {
        cy.log('TOTP required — use mockAuthenticatedUser for tests');
        return;
      }
      window.localStorage.setItem('ab_access_token', auth.accessToken);
      window.localStorage.setItem('ab_refresh_token', auth.refreshToken);
      window.sessionStorage.setItem('ab_user', JSON.stringify(auth.user));
    });
});

Cypress.Commands.add('mockAuthenticatedUser', () => {
  window.localStorage.setItem('ab_access_token', 'mock.jwt.token');
  window.localStorage.setItem('ab_refresh_token', 'mock-refresh');
  window.sessionStorage.setItem('ab_user', JSON.stringify({
    id: 1, email: 'user@amenbank.com', firstName: 'Mohamed',
    lastName: 'Ben Ali', status: 'ACTIVE', totpEnabled: true, roles: ['ROLE_USER']
  }));
});
