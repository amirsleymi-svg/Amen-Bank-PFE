"""
Chatbot brain — intent detection, FAQ answers, banking API calls.
"""

import json
import re
import httpx
from datetime import datetime
from typing import Optional

from app.config import settings
from app.database import get_redis


# ─── FAQ knowledge base ───────────────────────────────────────────────

FAQ = {
    "frais": {
        "topic": "frais",
        "answer": (
            "Chez Amen Bank, votre compte courant est **sans frais de tenue**. "
            "Les virements internes sont gratuits. Les virements externes sont facturés "
            "0.500 TND par opération. Aucun frais caché sur les opérations courantes."
        ),
        "actions": ["Voir la grille tarifaire", "Ouvrir un compte"],
    },
    "carte": {
        "topic": "carte",
        "answer": (
            "Nous proposons plusieurs types de cartes :\n"
            "• **Carte Visa Classic** — gratuite la première année\n"
            "• **Carte Visa Gold** — plafond élevé, assurance voyage incluse\n"
            "• **Carte Visa Business** — dédiée aux professionnels\n\n"
            "Pour commander votre carte, rendez-vous dans **Profil → Cartes**."
        ),
        "actions": ["Commander une carte", "Voir les plafonds"],
    },
    "credit": {
        "topic": "credit",
        "answer": (
            "Amen Bank propose plusieurs types de crédits :\n"
            "• **Personnel** — jusqu'à 100 000 TND, taux 8.95%\n"
            "• **Immobilier** — jusqu'à 500 000 TND, taux 7.25%\n"
            "• **Automobile** — jusqu'à 80 000 TND, taux 8.50%\n"
            "• **Étudiant** — jusqu'à 20 000 TND, taux 6.20%\n\n"
            "Utilisez notre simulateur pour estimer vos mensualités."
        ),
        "actions": ["Simuler un crédit", "Faire une demande"],
    },
    "virement": {
        "topic": "virement",
        "answer": (
            "Pour effectuer un virement :\n"
            "1. Allez dans **Virements**\n"
            "2. Saisissez l'IBAN du bénéficiaire\n"
            "3. Entrez le montant et le libellé\n"
            "4. Confirmez avec votre **code Google Authenticator** (6 chiffres)\n\n"
            "Les virements planifiés et les ordres permanents sont aussi disponibles."
        ),
        "actions": ["Faire un virement", "Ajouter un bénéficiaire"],
    },
    "sécurité": {
        "topic": "sécurité",
        "answer": (
            "Votre sécurité est notre priorité :\n"
            "• **2FA obligatoire** via Google Authenticator pour chaque opération sensible\n"
            "• **Chiffrement SSL 256-bit** sur toutes les communications\n"
            "• **Surveillance 24h/24** des transactions suspectes\n"
            "• **Codes de secours** disponibles si vous perdez accès à votre authenticateur\n\n"
            "En cas de problème, contactez immédiatement notre support au **+216 71 XXX XXX**."
        ),
        "actions": ["Activer la 2FA", "Signaler une activité suspecte"],
    },
    "iban": {
        "topic": "iban",
        "answer": (
            "Votre IBAN se trouve dans la section **Comptes** de votre espace client. "
            "Il commence par **TN59** suivi de 20 chiffres. "
            "Vous pouvez le copier directement depuis l'application ou le télécharger en PDF."
        ),
        "actions": ["Voir mes comptes", "Télécharger le RIB"],
    },
    "mdp": {
        "topic": "support",
        "answer": (
            "Pour réinitialiser votre mot de passe :\n"
            "1. Cliquez sur **Mot de passe oublié** sur la page de connexion\n"
            "2. Saisissez votre adresse email\n"
            "3. Suivez le lien reçu par email (valable 1 heure)\n\n"
            "Si vous avez perdu accès à votre email, contactez le support."
        ),
        "actions": ["Réinitialiser le mot de passe", "Contacter le support"],
    },
}

# ─── Intent keywords ──────────────────────────────────────────────────

INTENTS = {
    "balance":    ["solde", "balance", "combien", "disponible", "argent", "compte"],
    "transactions": ["transaction", "opération", "historique", "dernière", "mouvement", "relevé"],
    "credit":     ["crédit", "prêt", "emprunt", "mensualité", "taux", "financement", "loan"],
    "virement":   ["virement", "transfert", "envoyer", "payer", "bénéficiaire", "iban"],
    "carte":      ["carte", "visa", "débit", "retrait", "plafond", "mastercard"],
    "frais":      ["frais", "tarif", "commission", "coût", "prix", "gratuit"],
    "sécurité":   ["sécurité", "2fa", "authenticator", "piratage", "fraude", "bloqué", "suspect"],
    "iban":       ["iban", "rib", "coordonnées", "référence"],
    "mdp":        ["mot de passe", "password", "mdp", "oublié", "réinitialiser", "connexion"],
    "bonjour":    ["bonjour", "salut", "bonsoir", "hello", "hi", "hey", "coucou"],
    "merci":      ["merci", "thanks", "ok merci", "parfait", "super", "bonne"],
}


def detect_intent(message: str) -> str:
    text = message.lower()
    scores: dict[str, int] = {}

    for intent, keywords in INTENTS.items():
        score = sum(1 for kw in keywords if kw in text)
        if score > 0:
            scores[intent] = score

    return max(scores, key=scores.get) if scores else "default"


class ChatbotService:

    def __init__(self):
        self.redis = None

    async def get_redis(self):
        return get_redis()

    async def get_session(self, session_id: str) -> list[dict]:
        redis = await self.get_redis()
        raw = await redis.get(f"chat:session:{session_id}")
        return json.loads(raw) if raw else []

    async def save_session(self, session_id: str, history: list[dict]):
        redis = await self.get_redis()
        await redis.setex(
            f"chat:session:{session_id}",
            settings.SESSION_TTL,
            json.dumps(history[-settings.MAX_HISTORY:])
        )

    async def get_balance(self, user_id: int, token: str) -> Optional[str]:
        try:
            async with httpx.AsyncClient(timeout=5) as client:
                resp = await client.get(
                    f"{settings.BACKEND_URL}/accounts",
                    headers={"Authorization": f"Bearer {token}"}
                )
                if resp.status_code == 200:
                    accounts = resp.json().get("data", [])
                    if not accounts:
                        return "Vous n'avez pas encore de compte actif."
                    lines = []
                    for acc in accounts:
                        lines.append(
                            f"• **{acc['accountNumber']}** "
                            f"({acc.get('accountType','CHECKING')}) — "
                            f"**{float(acc['availableBalance']):.3f} TND** disponible"
                        )
                    return "Vos comptes :\n" + "\n".join(lines)
        except Exception:
            pass
        return None

    async def get_recent_transactions(self, user_id: int, account_id: int, token: str) -> Optional[str]:
        try:
            async with httpx.AsyncClient(timeout=5) as client:
                resp = await client.get(
                    f"{settings.BACKEND_URL}/accounts/{account_id}/transactions",
                    params={"page": 0, "size": 5},
                    headers={"Authorization": f"Bearer {token}"}
                )
                if resp.status_code == 200:
                    txs = resp.json().get("data", {}).get("content", [])
                    if not txs:
                        return "Aucune transaction récente."
                    lines = ["Vos 5 dernières transactions :"]
                    for tx in txs:
                        sign   = "−" if tx["type"] == "DEBIT" else "+"
                        amount = float(tx["amount"])
                        label  = tx.get("label") or tx.get("category", "Opération")
                        lines.append(f"• {sign}{amount:.3f} TND — {label} ({tx['valueDate']})")
                    return "\n".join(lines)
        except Exception:
            pass
        return None

    async def process(
        self,
        session_id: str,
        message: str,
        user_id: Optional[int] = None,
        token: Optional[str] = None,
    ) -> dict:
        # Load history
        history = await self.get_session(session_id)

        # Append user message
        history.append({"role": "user", "content": message, "ts": datetime.utcnow().isoformat()})

        # Detect intent
        intent = detect_intent(message)
        response_text = ""
        topic = intent
        actions = []

        # ── Greeting ───────────────────────────────────────────────
        if intent == "bonjour":
            response_text = (
                "Bonjour ! 👋 Je suis **l'assistant Amen Bank**. "
                "Comment puis-je vous aider aujourd'hui ?\n\n"
                "Je peux vous renseigner sur :\n"
                "• Votre solde et vos transactions\n"
                "• Les virements\n"
                "• Les crédits et simulations\n"
                "• Les tarifs et la sécurité"
            )
            actions = ["Voir mon solde", "Faire un virement", "Simuler un crédit"]
            topic = "greeting"

        # ── Thank you ──────────────────────────────────────────────
        elif intent == "merci":
            response_text = "Avec plaisir ! 😊 N'hésitez pas si vous avez d'autres questions."
            topic = "closing"

        # ── Balance (requires auth) ─────────────────────────────────
        elif intent == "balance":
            if user_id and token:
                fetched = await self.get_balance(user_id, token)
                response_text = fetched or "Je ne peux pas récupérer votre solde pour le moment. Veuillez vérifier votre connexion."
            else:
                response_text = "Pour consulter votre solde, veuillez vous connecter à votre espace client."
            actions = ["Voir mes comptes", "Télécharger le relevé"]

        # ── Transactions (requires auth) ────────────────────────────
        elif intent == "transactions":
            if user_id and token:
                response_text = "Consultez votre historique complet depuis **Comptes → Transactions**. Les filtres avancés vous permettent de rechercher par date, type et montant."
            else:
                response_text = "Connectez-vous pour accéder à votre historique de transactions."
            actions = ["Voir mes transactions", "Exporter en CSV"]

        # ── FAQ intents ─────────────────────────────────────────────
        elif intent in FAQ:
            entry = FAQ[intent]
            response_text = entry["answer"]
            topic = entry["topic"]
            actions = entry.get("actions", [])

        # ── Default ─────────────────────────────────────────────────
        else:
            response_text = (
                "Je n'ai pas bien compris votre question. "
                "Voici les sujets sur lesquels je peux vous aider :\n\n"
                "• **Solde** et **transactions**\n"
                "• **Virements** et bénéficiaires\n"
                "• **Crédits** et simulations\n"
                "• **Tarifs** et **sécurité**\n"
                "• **IBAN** et coordonnées bancaires\n\n"
                "Ou posez votre question différemment 😊"
            )
            actions = ["Voir mon solde", "Faire un virement", "Simuler un crédit"]
            topic = "unknown"

        # Append assistant response
        history.append({
            "role": "assistant",
            "content": response_text,
            "topic": topic,
            "ts": datetime.utcnow().isoformat()
        })

        await self.save_session(session_id, history)

        # Track analytics
        await self._track(session_id, intent, topic)

        return {
            "session_id": session_id,
            "message": response_text,
            "topic": topic,
            "suggested_actions": actions,
            "timestamp": datetime.utcnow().isoformat(),
        }

    async def _track(self, session_id: str, intent: str, topic: str):
        try:
            redis = await self.get_redis()
            await redis.incr(f"analytics:intent:{intent}")
            await redis.incr(f"analytics:topic:{topic}")
            await redis.incr("analytics:total_messages")
        except Exception:
            pass


chatbot_service = ChatbotService()
