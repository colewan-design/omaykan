<?php

namespace App\Services;

use RuntimeException;

/**
 * The drop-off pin is further from the store than DeliveryQuoter::MAX_KM.
 * Surfaces to the customer as a 422 — it is a fixable problem with the address,
 * not a server fault.
 */
class OutsideDeliveryAreaException extends RuntimeException
{
    public function __construct(string $message = "That address is outside this store's delivery area.")
    {
        parent::__construct($message);
    }
}
