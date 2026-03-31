"""
Chatbot brain: intent detection, FAQ answers, backend API calls, and Ollama fallback.
"""

from __future__ import annotations

import json
import logging
from datetime import datetime
from typing import Optional

import httpx

from app.config import settings
from app.database import get_redis

logger = logging.getLogger(__name__)


FAQ = {
    "frais": {
        "topic": "frais",
        "answer": (
            "Chez Amen Bank, votre compte courant est sans frais de tenue. "
            "Les virements internes sont gratuits. Les virements externes sont factures "
            "0.500 TND par operation."
        ),
        "actions": ["Voir la grille tarifaire", "Ouvrir un compte"],
    },
    "carte": {
        "topic": "carte",
        "answer": (
            "Nous proposons plusieurs cartes: Visa Classic, Visa Gold et Visa Business. "
            "Vous pouvez commander votre carte depuis Profil > Cartes."
        ),
        "actions": ["Commander une carte", "Voir les plafonds"],
    },
    "credit": {
        "topic": "credit",
        "answer": (
            "Amen Bank propose plusieurs types de credit:\n"
            "- Personnel: jusqu'a 100 000 TND, taux 8.95%\n"
            "- Immobilier: jusqu'a 500 000 TND, taux 7.25%\n"
            "- Automobile: jusqu'a 80 000 TND, taux 8.50%\n"
            "- Etudiant: jusqu'a 20 000 TND, taux 6.20%"
        ),
        "actions": ["Simuler un credit", "Faire une demande"],
    },
    "virement": {
        "topic": "virement",
        "answer": (
            "Pour effectuer un virement:\n"
            "1. Allez dans Virements\n"
            "2. Saisissez l'IBAN du beneficiaire\n"
            "3. Entrez le montant et le libelle\n"
            "4. Confirmez avec votre code Google Authenticator"
        ),
        "actions": ["Faire un virement", "Ajouter un beneficiaire"],
    },
    "sécurité": {
        "topic": "sécurité",
        "answer": (
            "Votre securite est notre priorite: 2FA obligatoire, chiffrement SSL, "
            "surveillance des transactions et codes de secours."
        ),
        "actions": ["Activer la 2FA", "Signaler une activite suspecte"],
    },
    "iban": {
        "topic": "iban",
        "answer": (
            "Votre IBAN est disponible dans la section Comptes de votre espace client. "
            "Il commence par TN59 suivi de 20 chiffres."
        ),
        "actions": ["Voir mes comptes", "Telecharger le RIB"],
    },
    "mdp": {
        "topic": "support",
        "answer": (
            "Pour reinitialiser votre mot de passe, utilisez Mot de passe oublie sur la "
            "page de connexion puis suivez le lien recu par email."
        ),
        "actions": ["Reinitialiser le mot de passe", "Contacter le support"],
    },
}


INTENTS = {
    "balance": ["solde", "balance", "combien", "disponible", "argent", "compte"],
    "transactions": ["transaction", "opération", "operation", "historique", "dernière", "derniere", "mouvement", "relevé", "releve"],
    "credit": ["crédit", "credit", "prêt", "pret", "emprunt", "mensualité", "mensualite", "taux", "financement", "loan"],
    "virement": ["virement", "transfert", "envoyer", "payer", "bénéficiaire", "beneficiaire", "iban"],
    "carte": ["carte", "visa", "débit", "debit", "retrait", "plafond", "mastercard"],
    "frais": ["frais", "tarif", "commission", "coût", "cout", "prix", "gratuit"],
    "sécurité": ["securite", "sécurité", "2fa", "authenticator", "piratage", "fraude", "bloque", "suspect"],
    "iban": ["iban", "rib", "coordonnées", "coordonnees", "référence", "reference"],
    "mdp": ["mot de passe", "password", "mdp", "oublié", "oublie", "réinitialiser", "reinitialiser", "connexion"],
    "bonjour": ["bonjour", "salut", "bonsoir", "hello", "hi", "hey", "coucou"],
    "merci": ["merci", "thanks", "ok merci", "parfait", "super", "bonne"],
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
        self._memory_sessions: dict[str, list[dict]] = {}
        self._memory_intents: dict[str, int] = {}
        self._memory_topics: dict[str, int] = {}
        self._memory_total_messages: int = 0

    async def get_redis(self):
        return get_redis()

    async def get_session(self, session_id: str) -> list[dict]:
        redis = await self.get_redis()
        if redis is not None:
            try:
                raw = await redis.get(f"chat:session:{session_id}")
                if raw:
                    return json.loads(raw)
            except Exception:
                pass
        return self._memory_sessions.get(session_id, [])

    async def save_session(self, session_id: str, history: list[dict]):
        trimmed = history[-settings.MAX_HISTORY :]
        self._memory_sessions[session_id] = trimmed

        redis = await self.get_redis()
        if redis is not None:
            try:
                await redis.setex(
                    f"chat:session:{session_id}",
                    settings.SESSION_TTL,
                    json.dumps(trimmed),
                )
            except Exception:
                pass

    async def clear_session(self, session_id: str):
        self._memory_sessions.pop(session_id, None)
        redis = await self.get_redis()
        if redis is not None:
            try:
                await redis.delete(f"chat:session:{session_id}")
            except Exception:
                pass

    async def _fetch_accounts(self, token: str) -> list[dict]:
        try:
            async with httpx.AsyncClient(timeout=5) as client:
                resp = await client.get(
                    f"{settings.BACKEND_URL}/accounts",
                    headers={"Authorization": f"Bearer {token}"},
                )
                if resp.status_code == 200:
                    return resp.json().get("data", [])
        except Exception:
            pass
        return []

    async def get_balance(self, user_id: int, token: str) -> Optional[str]:
        _ = user_id
        accounts = await self._fetch_accounts(token)
        if not accounts:
            return "Vous n'avez pas encore de compte actif."

        lines = []
        for acc in accounts:
            lines.append(
                f"- {acc['accountNumber']} ({acc.get('accountType', 'CHECKING')}): "
                f"{float(acc['availableBalance']):.3f} TND disponible"
            )
        return "Vos comptes:\n" + "\n".join(lines)

    async def get_recent_transactions(self, account_id: int, token: str) -> Optional[str]:
        try:
            async with httpx.AsyncClient(timeout=5) as client:
                resp = await client.get(
                    f"{settings.BACKEND_URL}/accounts/{account_id}/transactions",
                    params={"page": 0, "size": 5},
                    headers={"Authorization": f"Bearer {token}"},
                )
                if resp.status_code == 200:
                    txs = resp.json().get("data", {}).get("content", [])
                    if not txs:
                        return "Aucune transaction recente."
                    lines = ["Vos 5 dernieres transactions:"]
                    for tx in txs:
                        sign = "-" if tx["type"] == "DEBIT" else "+"
                        amount = float(tx["amount"])
                        label = tx.get("label") or tx.get("category", "Operation")
                        lines.append(f"{sign}{amount:.3f} TND - {label} ({tx['valueDate']})")
                    return "\n".join(lines)
        except Exception:
            pass
        return None

    async def _ask_ollama(self, message: str, history: list[dict]) -> Optional[str]:
        if not settings.OLLAMA_ENABLED:
            return None

        short_history = history[-6:]
        conversation = []
        for item in short_history:
            role = item.get("role", "user")
            content = str(item.get("content", "")).strip()
            if content:
                conversation.append(f"{role}: {content}")

        prompt = (
            "Tu es l'assistant Amen Bank. Reponds en francais, clairement et sans inventer "
            "des donnees personnelles. Si la question touche le solde/transactions sans preuve "
            "d'authentification, demande une connexion.\n\n"
            f"Historique:\n{chr(10).join(conversation)}\n\n"
            f"Question utilisateur: {message}\n"
            "Reponse:"
        )

        payload = {
            "model": settings.OLLAMA_MODEL,
            "prompt": prompt,
            "stream": False,
            "options": {"temperature": 0.2},
        }

        try:
            async with httpx.AsyncClient(timeout=settings.OLLAMA_TIMEOUT_SECONDS) as client:
                resp = await client.post(f"{settings.OLLAMA_BASE_URL}/api/generate", json=payload)
                if resp.status_code != 200:
                    logger.warning(
                        "Ollama returned HTTP %s for model '%s': %s",
                        resp.status_code, settings.OLLAMA_MODEL, resp.text[:200],
                    )
                    return None
                text = (resp.json().get("response") or "").strip()
                return text[:2000] if text else None
        except httpx.ConnectError:
            logger.error("Cannot connect to Ollama at %s — is it running?", settings.OLLAMA_BASE_URL)
            return None
        except httpx.TimeoutException:
            logger.warning("Ollama timed out after %ss for model '%s'", settings.OLLAMA_TIMEOUT_SECONDS, settings.OLLAMA_MODEL)
            return None
        except Exception as exc:
            logger.exception("Unexpected error calling Ollama: %s", exc)
            return None

    async def process(
        self,
        session_id: str,
        message: str,
        user_id: Optional[int] = None,
        token: Optional[str] = None,
    ) -> dict:
        history = await self.get_session(session_id)
        history.append({"role": "user", "content": message, "ts": datetime.utcnow().isoformat()})

        intent = detect_intent(message)
        response_text = ""
        topic = intent
        actions: list[str] = []

        if intent == "bonjour":
            response_text = (
                "Bonjour! Je suis l'assistant Amen Bank. "
                "Je peux vous aider pour le solde, les transactions, les virements et les credits."
            )
            actions = ["Voir mon solde", "Faire un virement", "Simuler un credit"]
            topic = "greeting"

        elif intent == "merci":
            response_text = "Avec plaisir. N'hesitez pas si vous avez d'autres questions."
            topic = "closing"

        elif intent == "balance":
            if user_id and token:
                fetched = await self.get_balance(user_id, token)
                response_text = (
                    fetched
                    or "Je ne peux pas recuperer votre solde pour le moment. Veuillez reessayer."
                )
            else:
                response_text = "Pour consulter votre solde, veuillez vous connecter."
            actions = ["Voir mes comptes", "Telecharger le releve"]

        elif intent == "transactions":
            if user_id and token:
                accounts = await self._fetch_accounts(token)
                if accounts:
                    first_account_id = accounts[0].get("id")
                    if isinstance(first_account_id, int):
                        fetched = await self.get_recent_transactions(first_account_id, token)
                        response_text = fetched or (
                            "Je ne peux pas recuperer vos transactions pour le moment."
                        )
                    else:
                        response_text = "Aucun compte exploitable trouve pour afficher les transactions."
                else:
                    response_text = "Vous n'avez pas encore de compte actif."
            else:
                response_text = "Connectez-vous pour acceder a votre historique de transactions."
            actions = ["Voir mes transactions", "Exporter en CSV"]

        elif intent in FAQ:
            entry = FAQ[intent]
            response_text = entry["answer"]
            topic = entry["topic"]
            actions = entry.get("actions", [])

        else:
            llm_response = await self._ask_ollama(message, history)
            if llm_response:
                response_text = llm_response
                topic = "unknown"
            else:
                response_text = (
                    "Je n'ai pas bien compris votre question. Je peux vous aider sur: "
                    "solde, transactions, virements, credits, tarifs, securite et IBAN."
                )
                actions = ["Voir mon solde", "Faire un virement", "Simuler un credit"]
                topic = "unknown"

        history.append(
            {
                "role": "assistant",
                "content": response_text,
                "topic": topic,
                "ts": datetime.utcnow().isoformat(),
            }
        )

        await self.save_session(session_id, history)
        await self._track(intent, topic)

        return {
            "session_id": session_id,
            "message": response_text,
            "topic": topic,
            "suggested_actions": actions,
            "timestamp": datetime.utcnow().isoformat(),
        }

    async def _track(self, intent: str, topic: str):
        self._memory_total_messages += 1
        self._memory_intents[intent] = self._memory_intents.get(intent, 0) + 1
        self._memory_topics[topic] = self._memory_topics.get(topic, 0) + 1

        redis = await self.get_redis()
        if redis is not None:
            try:
                await redis.incr(f"analytics:intent:{intent}")
                await redis.incr(f"analytics:topic:{topic}")
                await redis.incr("analytics:total_messages")
            except Exception:
                pass

    def get_memory_analytics(self) -> dict:
        return {
            "total_messages": self._memory_total_messages,
            "top_intents": sorted(self._memory_intents.items(), key=lambda x: x[1], reverse=True)[:10],
            "top_topics": sorted(self._memory_topics.items(), key=lambda x: x[1], reverse=True)[:10],
        }


chatbot_service = ChatbotService()
