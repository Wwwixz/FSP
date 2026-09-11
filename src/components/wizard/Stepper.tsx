import { STEPS, type StepId } from "../../types/wizard";

interface StepperProps {
  currentStep: StepId;
}

export default function Stepper({ currentStep }: StepperProps) {
  return (
    <ol className="flex flex-wrap items-center gap-x-6 gap-y-2 border-b border-line pb-5">
      {STEPS.map((step) => {
        const isActive = step.id === currentStep;
        const isDone = step.id < currentStep;

        return (
          <li key={step.id} className="flex items-center gap-2">
            <span
              className={[
                "flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-medium",
                isActive
                  ? "bg-accent-600 text-white"
                  : isDone
                    ? "bg-accent-100 text-accent-600"
                    : "border border-ink-400/40 text-ink-400",
              ].join(" ")}
            >
              {step.id}
            </span>
            <span
              className={[
                "text-sm",
                isActive ? "font-medium text-ink-900" : "text-ink-400",
              ].join(" ")}
            >
              {step.label}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
