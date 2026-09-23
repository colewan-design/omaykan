<?php

namespace App\Services;

/**
 * An image ImageStore would not keep. The message is written for the person
 * who picked the file, not for a log.
 */
class ImageRejected extends \RuntimeException
{
}
