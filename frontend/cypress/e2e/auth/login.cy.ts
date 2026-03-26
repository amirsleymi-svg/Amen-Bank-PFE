// cypress/e2e/auth/login.cy.ts

describe('Login Flow', () => {

  beforeEach(() => {
    cy.visit('/auth/login');
  });

  // ── Page renders correctly ─────────────────────────────────────
  it('should display the login form with all elements', () => {
    cy.get('h2').should('contain', 'Connexion');
    cy.get('#identifier').should('exist');
    cy.get('#password').should('exist');
    cy.get('button[type=submit]').should('contain', 'Se connecter');
    cy.get('a[routerlink="/auth/register"]').should('exist');
    cy.get('a[routerlink="/auth/forgot-password"]').should('exist');
  });

  // ── Validation ─────────────────────────────────────────────────
  it('should show validation errors when submitting empty form', () => {
    cy.get('button[type=submit]').click();
    cy.get('.field-error').should('have.length.at.least', 1);
  });

  // ── Wrong credentials ──────────────────────────────────────────
  it('should display error on wrong credentials', () => {
    cy.intercept('POST', '/api/v1/auth/login', {
      statusCode: 401,
      body: { success: false, message: 'Identifiants incorrects. Veuillez réessayer.' }
    }).as('loginFail');

    cy.get('#identifier').type('wrong@user.com');
    cy.get('#password').type('WrongPass123!');
    cy.get('button[type=submit]').click();

    cy.wait('@loginFail');
    cy.get('.alert-error').should('be.visible');
    cy.get('.alert-error').should('contain', 'Identifiants incorrects');
  });

  // ── Successful login → TOTP modal ─────────────────────────────
  it('should show TOTP step when 2FA is required', () => {
    cy.intercept('POST', '/api/v1/auth/login', {
      statusCode: 200,
      body: {
        success: true,
        data: { totpRequired: true, tempToken: 'mock-temp-token-123' }
      }
    }).as('loginOk');

    cy.get('#identifier').type('user@amenbank.com');
    cy.get('#password').type('Test@1234!');
    cy.get('button[type=submit]').click();

    cy.wait('@loginOk');
    cy.get('.totp-form').should('be.visible');
    cy.get('.totp-digit').should('have.length', 6);
  });

  // ── TOTP input ─────────────────────────────────────────────────
  it('should fill TOTP digits and submit', () => {
    cy.intercept('POST', '/api/v1/auth/login', {
      statusCode: 200,
      body: { success: true, data: { totpRequired: true, tempToken: 'mock-token' } }
    });
    cy.intercept('POST', '/api/v1/auth/totp/verify', {
      statusCode: 200,
      body: {
        success: true,
        data: {
          accessToken: 'mock.jwt.token',
          refreshToken: 'mock-refresh',
          expiresIn: 900,
          user: { id: 1, email: 'user@amenbank.com', firstName: 'Test', status: 'ACTIVE', totpEnabled: true }
        }
      }
    }).as('totpVerify');

    cy.get('#identifier').type('user@amenbank.com');
    cy.get('#password').type('Test@1234!');
    cy.get('button[type=submit]').click();

    // Type TOTP digits
    '123456'.split('').forEach((d, i) => {
      cy.get(`#totp-${i}`).type(d);
    });

    cy.get('.totp-form button[type=submit]').click();
    cy.wait('@totpVerify');
    cy.url().should('include', '/dashboard');
  });

  // ── Password visibility toggle ─────────────────────────────────
  it('should toggle password visibility', () => {
    cy.get('#password').should('have.attr', 'type', 'password');
    cy.get('.toggle-password').click();
    cy.get('#password').should('have.attr', 'type', 'text');
    cy.get('.toggle-password').click();
    cy.get('#password').should('have.attr', 'type', 'password');
  });

  // ── Navigation ─────────────────────────────────────────────────
  it('should navigate to register page', () => {
    cy.get('a[routerlink="/auth/register"]').click();
    cy.url().should('include', '/auth/register');
  });

  it('should navigate to forgot password page', () => {
    cy.get('.forgot-link').click();
    cy.url().should('include', '/auth/forgot-password');
  });
});
