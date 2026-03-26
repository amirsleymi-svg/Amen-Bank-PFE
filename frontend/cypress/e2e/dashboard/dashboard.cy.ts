// cypress/e2e/dashboard/dashboard.cy.ts

describe('Dashboard', () => {

  beforeEach(() => {
    // Mock auth state
    cy.window().then(win => {
      win.localStorage.setItem('ab_access_token', 'mock.jwt.access.token');
      win.localStorage.setItem('ab_refresh_token', 'mock-refresh-token');
      win.sessionStorage.setItem('ab_user', JSON.stringify({
        id: 1, email: 'user@amenbank.com',
        firstName: 'Mohamed', lastName: 'Ben Ali',
        status: 'ACTIVE', totpEnabled: true
      }));
    });

    // Mock API calls
    cy.intercept('GET', '/api/v1/auth/me', {
      statusCode: 200,
      body: { success: true, data: {
        id: 1, email: 'user@amenbank.com', firstName: 'Mohamed',
        lastName: 'Ben Ali', status: 'ACTIVE', totpEnabled: true,
        roles: ['ROLE_USER']
      }}
    }).as('getMe');

    cy.intercept('GET', '/api/v1/accounts', {
      statusCode: 200,
      body: { success: true, data: [
        { id: 1, accountNumber: 'TN00123456', iban: 'TN5900440100012345678901',
          accountType: 'CHECKING', currency: 'TND', balance: 12500.750,
          availableBalance: 12500.750, status: 'ACTIVE',
          dailyLimit: 5000, monthlyLimit: 50000 }
      ]}
    }).as('getAccounts');

    cy.intercept('GET', '/api/v1/accounts/1/transactions*', {
      statusCode: 200,
      body: { success: true, data: {
        content: [
          { id: 1, type: 'DEBIT', category: 'TRANSFER', amount: 500,
            currency: 'TND', label: 'Loyer', valueDate: '2024-01-15',
            status: 'COMPLETED', balanceAfter: 12000 }
        ],
        totalElements: 1, totalPages: 1, page: 0, size: 8
      }}
    }).as('getTransactions');

    cy.visit('/dashboard');
  });

  // ── Renders correctly ──────────────────────────────────────────
  it('should display welcome message with user name', () => {
    cy.get('.welcome-banner h2').should('contain', 'Mohamed');
  });

  it('should display account card with balance', () => {
    cy.wait('@getAccounts');
    cy.get('.account-card').should('have.length.at.least', 1);
    cy.get('.balance-amount').should('be.visible');
  });

  it('should display recent transactions', () => {
    cy.wait('@getTransactions');
    cy.get('.tx-item').should('have.length.at.least', 1);
    cy.get('.tx-label').first().should('contain', 'Loyer');
  });

  it('should show total balance in header', () => {
    cy.wait('@getAccounts');
    cy.get('.total-balance').should('be.visible');
  });

  // ── Navigation ─────────────────────────────────────────────────
  it('should navigate to transfers page', () => {
    cy.get('[routerlink="/dashboard/transfers"]').first().click();
    cy.url().should('include', '/dashboard/transfers');
  });

  it('should navigate to credits page', () => {
    cy.get('[routerlink="/dashboard/credits"]').first().click();
    cy.url().should('include', '/dashboard/credits');
  });

  // ── Sidebar ────────────────────────────────────────────────────
  it('should toggle sidebar on collapse button click', () => {
    cy.get('.sidebar-toggle').click();
    cy.get('.sidebar').should('have.class', 'collapsed');
    cy.get('.sidebar-toggle').click();
    cy.get('.sidebar').should('not.have.class', 'collapsed');
  });

  // ── Chatbot ────────────────────────────────────────────────────
  it('should open chatbot when toggle button clicked', () => {
    cy.get('.chatbot-toggle').click();
    cy.get('.chat-window').should('be.visible');
  });

  // ── Auth guard ─────────────────────────────────────────────────
  it('should redirect to login if no token', () => {
    cy.window().then(win => { win.localStorage.clear(); win.sessionStorage.clear(); });
    cy.visit('/dashboard');
    cy.url().should('include', '/auth/login');
  });
});
