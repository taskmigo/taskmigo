import Link from 'next/link';

export default function HomePage() {
  return (
    <div className='flex flex-1 flex-col justify-center text-center'>
      <h1 className='mb-4 text-2xl font-bold'>Welcome to Taskmigo</h1>
      <p>
        You can open{' '}
        <Link href='/versions' className='font-medium underline'>
          /versions
        </Link>{' '}
        and see the documentation.
      </p>
    </div>
  );
}
