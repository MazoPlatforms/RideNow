import { ZodError } from 'zod';
export class AppError extends Error {
  constructor(status, code, message = code, details) {
    super(message);
    Object.assign(this, { status, code, details });
  }
}
export function ensure(value, status = 409, code = 'CONFLICT') {
  if (!value) throw new AppError(status, code);
  return value;
}
export function errorHandler(logger) {
  return (err, req, res, next) => {
    if (res.headersSent) return next(err);
    if (err instanceof ZodError)
      err = new AppError(
        400,
        'VALIDATION_ERROR',
        'Invalid request',
        err.issues.map((i) => ({ path: i.path, message: i.message })),
      );
    if (['23505', '23503', '23514', '23P01'].includes(err.code))
      err = new AppError(409, 'CONSTRAINT_CONFLICT');
    if (err.type === 'entity.parse.failed') err = new AppError(400, 'INVALID_JSON');
    if (err.status === 413) err = new AppError(413, 'PAYLOAD_TOO_LARGE');
    if (!err.status) logger.error({ err, requestId: req.id }, 'Request failed');
    res.status(err.status || 500).json({
      code: err.status ? err.code : 'INTERNAL_ERROR',
      message: err.status ? err.message : 'Internal error',
      ...(err.details ? { details: err.details } : {}),
      requestId: req.id,
    });
  };
}
