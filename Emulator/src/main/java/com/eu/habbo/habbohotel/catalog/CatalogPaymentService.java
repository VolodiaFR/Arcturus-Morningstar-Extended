package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.users.UserCreditsComposer;
import com.eu.habbo.messages.outgoing.users.UserPointsComposer;

/** Catalog-only facade for one atomic credits-plus-points wallet mutation. */
public final class CatalogPaymentService {
    private CatalogPaymentService() {
    }

    public static boolean tryTake(Habbo habbo, int credits, int pointsType, int points) {
        if (habbo == null || !habbo.getHabboInfo().tryDebitCatalogPayment(credits, pointsType, points)) {
            return false;
        }

        if (habbo.getClient() != null) {
            if (credits > 0) habbo.getClient().sendResponse(new UserCreditsComposer(habbo));
            if (points > 0) {
                habbo.getClient().sendResponse(new UserPointsComposer(
                        habbo.getHabboInfo().getCurrencyAmount(pointsType), -points, pointsType));
            }
        }
        return true;
    }

    public static void refund(Habbo habbo, int credits, int pointsType, int points) {
        habbo.getHabboInfo().refundCatalogPayment(credits, pointsType, points);

        if (habbo.getClient() != null) {
            if (credits > 0) habbo.getClient().sendResponse(new UserCreditsComposer(habbo));
            if (points > 0) {
                habbo.getClient().sendResponse(new UserPointsComposer(
                        habbo.getHabboInfo().getCurrencyAmount(pointsType), points, pointsType));
            }
        }
    }
}
